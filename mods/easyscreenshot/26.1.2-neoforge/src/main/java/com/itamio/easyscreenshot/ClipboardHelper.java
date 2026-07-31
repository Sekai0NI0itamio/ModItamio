package com.itamio.easyscreenshot;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class ClipboardHelper {

    public enum Platform {
        MAC, WINDOWS, LINUX, UNKNOWN
    }

    private static Platform detectedPlatform = null;

    public static Platform getPlatform() {
        if (detectedPlatform == null) {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("mac")) {
                detectedPlatform = Platform.MAC;
            } else if (os.contains("win")) {
                detectedPlatform = Platform.WINDOWS;
            } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                detectedPlatform = Platform.LINUX;
            } else {
                detectedPlatform = Platform.UNKNOWN;
            }
        }
        return detectedPlatform;
    }

    public static void copyImageToClipboard(File imageFile) {
        try {
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                EasyScreenshot.LOGGER.warn("Failed to read image file for clipboard: {}", imageFile);
                return;
            }
            ImageTransferable transferable = new ImageTransferable(image);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferable, null);
            EasyScreenshot.LOGGER.info("Image copied to clipboard: {}", imageFile.getName());
        } catch (IOException e) {
            EasyScreenshot.LOGGER.warn("Failed to copy image to clipboard", e);
        }
    }

    public static void copyTextToClipboard(String text) {
        StringSelection selection = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
    }

    static class ImageTransferable implements Transferable {
        private final Image image;

        ImageTransferable(Image image) {
            this.image = image;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return image;
        }
    }
}
