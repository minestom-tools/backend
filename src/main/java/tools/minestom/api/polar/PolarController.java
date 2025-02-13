package tools.minestom.api.polar;

import net.hollowcube.polar.AnvilPolar;
import net.hollowcube.polar.ChunkSelector;
import net.hollowcube.polar.PolarWorld;
import net.hollowcube.polar.PolarWriter;
import org.apache.commons.io.FileUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RestController
@RequestMapping("/convert")
@CrossOrigin
public class PolarController {

    @PostMapping
    public ResponseEntity<byte[]> convertWorldToPolar(@RequestParam("file") MultipartFile file,
                                                      @RequestParam(value = "min", required = false, defaultValue = "-4") Integer min,
                                                      @RequestParam(value = "max", required = false, defaultValue = "19") Integer max,
                                                      @RequestParam(value = "center-x", required = false, defaultValue = "0") int centerX,
                                                      @RequestParam(value = "center-z", required = false, defaultValue = "0") int centerZ,
                                                      @RequestParam(value = "radius", required = false) Integer radius) {
        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("polar-convert");
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error creating temporary directory: " + e.getMessage()).getBytes());
        }

        Path zipFile = tempDir.resolve("input.zip");
        System.out.println("Received world file");

        try (InputStream is = file.getInputStream();
             OutputStream output = Files.newOutputStream(zipFile)) {
            is.transferTo(output);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error writing zip file: " + e.getMessage()).getBytes());
        }

        // unzip the world
        // thanks baeldung for the unzip code
        Path worldDir = tempDir.resolve("world");

        byte[] buffer = new byte[1024];
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                File newFile = newFile(worldDir.toFile(), entry);
                if (entry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }
                } else {
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }

                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }

                entry = zis.getNextEntry();
            }
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error unzipping file: " + e.getMessage()).getBytes());
        }

        PolarWorld world;
        try {
            ChunkSelector selector = radius == null ? ChunkSelector.all() : ChunkSelector.radius(centerX, centerZ, radius);
            world = AnvilPolar.anvilToPolar(worldDir, min, max, selector);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                    .body(("Error converting world to polar: " + e.getMessage()).getBytes());
        }

        byte[] polarData = PolarWriter.write(world);
        System.out.println("Polar data length: " + polarData.length);
        System.out.println("Converted world to polar format");

        try {
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"world.polar\"")
                    .header("Content-Type", "application/octet-stream")
                    .body(polarData);
        } finally {
            try {
                FileUtils.deleteDirectory(tempDir.toFile());
            } catch (IOException e) {
                System.err.println("Error cleaning up temp directory: " + e.getMessage());
            }
        }
    }

    private File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }
}
