package com.mycompany.servidordriveftp;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import java.io.*;
import java.security.Key;

public class MainDescargarYDescifrar {

    private static final String SERVIDOR_FTP = "127.0.0.1"; // Dirección del servidor FTP
    private static final int PUERTO_FTP = 21; // Puerto FTP
    private static final String USUARIO_FTP = "jeroenjm"; // Usuario FTP
    private static final String CONTRASEÑA_FTP = "12345"; // Contraseña FTP
    private static final String CARPETA_REMOTA = "remoto"; // Carpeta remota en el servidor FTP
    private static final String CARPETA_LOCAL_DESCIFRADOS = "C:\\Users\\MrJeroen10\\Desktop\\archivosdescifrados"; // Carpeta local para archivos descifrados
    private static final String CLAVE_AES = "1234567890123456"; // Clave AES para descifrar

    public static void main(String[] args) {
        // Crear la carpeta local si no existe
        File carpetaDescifrados = new File(CARPETA_LOCAL_DESCIFRADOS);
        if (!carpetaDescifrados.exists()) {
            carpetaDescifrados.mkdirs();
        }

        FTPClient ftpCliente = new FTPClient();
        try {
            // Conectar al servidor FTP
            ftpCliente.connect(SERVIDOR_FTP, PUERTO_FTP);
            ftpCliente.login(USUARIO_FTP, CONTRASEÑA_FTP);
            ftpCliente.enterLocalPassiveMode();
            ftpCliente.setFileType(FTP.BINARY_FILE_TYPE);

            // Cambiar a la carpeta remota
            if (ftpCliente.changeWorkingDirectory(CARPETA_REMOTA)) {
                System.out.println("Cambiado a la carpeta remota: " + CARPETA_REMOTA);

                // Listar archivos en la carpeta remota
                String[] archivosRemotos = ftpCliente.listNames();
                if (archivosRemotos != null) {
                    for (String archivoRemoto : archivosRemotos) {
                        if (archivoRemoto.endsWith(".txt")) {
                            // Descargar y descifrar el archivo
                            descargarYDescifrarArchivo(ftpCliente, archivoRemoto, carpetaDescifrados);
                        }
                    }
                } else {
                    System.out.println("No hay archivos en la carpeta remota.");
                }
            } else {
                System.err.println("No se pudo cambiar a la carpeta remota: " + CARPETA_REMOTA);
            }
        } catch (Exception e) {
            System.err.println("Error al conectar o listar archivos en el servidor FTP: " + e.getMessage());
        } finally {
            desconectarFTP(ftpCliente);
        }
    }

    private static void descargarYDescifrarArchivo(FTPClient ftpCliente, String archivoRemoto, File carpetaDescifrados) {
        try {
            // Crear un archivo temporal para almacenar el archivo cifrado descargado
            File archivoCifrado = new File(carpetaDescifrados, archivoRemoto + ".cifrado");

            // Descargar el archivo cifrado desde el servidor FTP
            try (FileOutputStream fos = new FileOutputStream(archivoCifrado)) {
                if (ftpCliente.retrieveFile(archivoRemoto, fos)) {
                    System.out.println("Archivo cifrado descargado: " + archivoRemoto);

                    // Descifrar el archivo
                    File archivoDescifrado = descifrarArchivo(archivoCifrado, carpetaDescifrados);
                    System.out.println("Archivo descifrado guardado en: " + archivoDescifrado.getAbsolutePath());
                } else {
                    System.err.println("Error al descargar el archivo: " + archivoRemoto);
                }
            }

            // Eliminar el archivo temporal cifrado
            archivoCifrado.delete();
        } catch (Exception e) {
            System.err.println("Error al descargar y descifrar el archivo " + archivoRemoto + ": " + e.getMessage());
        }
    }

    private static File descifrarArchivo(File archivoCifrado, File carpetaDescifrados) throws Exception {
        Key clave = AESSimpleManager.obtenerClave(CLAVE_AES, 16);

        // Leer el contenido cifrado del archivo
        BufferedReader br = new BufferedReader(new FileReader(archivoCifrado));
        StringBuilder textoCifrado = new StringBuilder();
        String linea;
        while ((linea = br.readLine()) != null) {
            textoCifrado.append(linea);
        }
        br.close();

        // Descifrar el contenido
        String textoEnClaro = AESSimpleManager.descifrar(textoCifrado.toString(), clave);

        // Crear el archivo descifrado en la carpeta local
        String nombreArchivoDescifrado = archivoCifrado.getName().replace(".cifrado", "");
        File archivoDescifrado = new File(carpetaDescifrados, nombreArchivoDescifrado);

        try (PrintWriter pw = new PrintWriter(archivoDescifrado)) {
            pw.write(textoEnClaro);
        }

        return archivoDescifrado;
    }

    private static void desconectarFTP(FTPClient ftpCliente) {
        try {
            if (ftpCliente.isConnected()) {
                ftpCliente.logout();
                ftpCliente.disconnect();
            }
        } catch (IOException e) {
            System.err.println("Error al cerrar la conexión FTP: " + e.getMessage());
        }
    }
}