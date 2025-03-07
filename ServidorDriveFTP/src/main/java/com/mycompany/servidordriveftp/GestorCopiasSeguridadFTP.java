package com.mycompany.servidordriveftp;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.file.*;
import java.security.Key;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GestorCopiasSeguridadFTP {

    private static final String SERVIDOR_FTP = "127.0.0.1";
    private static final int PUERTO_FTP = 21;
    private static final String USUARIO_FTP = "jeroenjm";
    private static final String CONTRASEÑA_FTP = "12345";
    private static final String CARPETA_LOCAL = "C:\\Users\\MrJeroen10\\Desktop\\remoto";
    private static final String CARPETA_REMOTA = "remoto";
    private static final ExecutorService servicioHilos = Executors.newCachedThreadPool(); //Para usar hilos
    private static final String CLAVE_AES = "1234567890123456";

    public static void main(String[] args) throws IOException {
        Path path = Paths.get(CARPETA_LOCAL);
        WatchService watchService = FileSystems.getDefault().newWatchService();
        path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);

        System.out.println("Monitorizando cambios en: " + CARPETA_LOCAL);

        while (true) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                System.err.println("Error en la monitorización: " + e.getMessage());
                return;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                Path filePath = path.resolve((Path) event.context());
                File archivo = filePath.toFile();

                /**
                 * Todas las operaciones FTP (subirArchivo, subirDirectorio, 
                 * eliminarElemento) se ejecutan en hilos separados usando servicioHilos.submit().
                 * Esto permite que el programa siga monitorizando cambios en la carpeta 
                 * local mientras se realizan operaciones en el servidor FTP.
                 */
                if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                    if (archivo.isDirectory()) {
                        servicioHilos.submit(() -> subirDirectorio(archivo));
                    } else {
                        servicioHilos.submit(() -> subirArchivo(archivo, ""));
                    }
                } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                    if (archivo.isFile()) {
                        servicioHilos.submit(() -> subirArchivo(archivo, ""));
                    }
                } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                    servicioHilos.submit(() -> eliminarElemento(archivo));
                }
            }
            key.reset();
        }
    }

    //Cada operación FTP está envuelta en un bloque try-catch para manejar 
    //excepciones y evitar que el programa falle sin nosotros saberlo.
    private static void subirArchivo(File archivo, String subdirectorio) {
        if (!archivo.exists()) return;
        servicioHilos.submit(() -> {
            FTPClient ftpCliente = new FTPClient();
            try {
                ftpCliente.connect(SERVIDOR_FTP, PUERTO_FTP);
                ftpCliente.login(USUARIO_FTP, CONTRASEÑA_FTP);
                ftpCliente.enterLocalPassiveMode();
                ftpCliente.setFileType(FTP.BINARY_FILE_TYPE);

                String rutaRemota = CARPETA_REMOTA + "/" + subdirectorio + archivo.getName();

                if (archivo.getName().endsWith(".txt")) {
                    // Cifrar el archivo antes de subirlo
                    File archivoCifrado = cifrarArchivo(archivo);
                    try (FileInputStream fis = new FileInputStream(archivoCifrado)) {
                        if (ftpCliente.storeFile(rutaRemota, fis)) {
                            System.out.println("Archivo cifrado y subido: " + archivo.getName());
                        }
                    }
                    archivoCifrado.delete(); // Eliminar el archivo cifrado temporal
                } else {
                    try (FileInputStream fis = new FileInputStream(archivo)) {
                        if (ftpCliente.storeFile(rutaRemota, fis)) {
                            System.out.println("Archivo subido: " + archivo.getName());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error al subir archivo: " + e.getMessage());
            } finally {
                desconectarFTP(ftpCliente);
            }
        });
    }

    private static File cifrarArchivo(File archivo) throws Exception {
        Key clave = AESSimpleManager.obtenerClave(CLAVE_AES, 16);
        BufferedReader br = new BufferedReader(new FileReader(archivo));
        StringBuilder textoEnClaro = new StringBuilder();
        String linea;
        while ((linea = br.readLine()) != null) {
            textoEnClaro.append(linea).append("\n");
        }
        br.close();

        String textoCifrado = AESSimpleManager.cifrar(textoEnClaro.toString(), clave);

        // Crear un archivo temporal con el contenido cifrado
        File archivoCifrado = File.createTempFile("temp_", ".txt.cifrado");
        PrintWriter pw = new PrintWriter(archivoCifrado);
        pw.write(textoCifrado);
        pw.close();

        return archivoCifrado;
    }

    private static void subirDirectorio(File directorio) {
        servicioHilos.submit(() -> {
            FTPClient ftpCliente = new FTPClient();
            try {
                ftpCliente.connect(SERVIDOR_FTP, PUERTO_FTP);
                ftpCliente.login(USUARIO_FTP, CONTRASEÑA_FTP);
                ftpCliente.enterLocalPassiveMode();

                String rutaRemota = CARPETA_REMOTA + "/" + directorio.getName();
                if (!ftpCliente.changeWorkingDirectory(rutaRemota)) {
                    // Crear el directorio si no existe
                    if (ftpCliente.makeDirectory(rutaRemota)) {
                        System.out.println("Directorio creado en remoto: " + rutaRemota);
                    }
                }
                for (File archivo : directorio.listFiles()) {
                    if (archivo.isDirectory()) {
                        subirDirectorio(archivo); // Llamada recursiva para carpetas
                    } else {
                        subirArchivo(archivo, directorio.getName() + "/");
                    }
                }
            } catch (IOException e) {
                System.err.println("Error al crear directorio en remoto: " + e.getMessage());
            } finally {
                desconectarFTP(ftpCliente);
            }
        });
    }

    private static void eliminarElemento(File archivo) {
        servicioHilos.submit(() -> {
            FTPClient ftpCliente = new FTPClient();
            try {
                ftpCliente.connect(SERVIDOR_FTP, PUERTO_FTP);
                ftpCliente.login(USUARIO_FTP, CONTRASEÑA_FTP);
                ftpCliente.enterLocalPassiveMode();
                String rutaRemota = CARPETA_REMOTA + "/" + archivo.getName();
                ftpCliente.removeDirectory(rutaRemota);
                ftpCliente.deleteFile(rutaRemota);
                System.out.println("Elemento eliminado: " + rutaRemota);
            } catch (IOException e) {
                System.err.println("Error al eliminar elemento: " + e.getMessage());
            } finally {
                desconectarFTP(ftpCliente);
            }
        });
    }

    private static void desconectarFTP(FTPClient ftpCliente) {
        try {
            if (ftpCliente.isConnected()) {
                ftpCliente.logout();
                ftpCliente.disconnect();
            }
        } catch (IOException e) {
            System.err.println("Error al cerrar conexión FTP: " + e.getMessage());
        }
    }
}