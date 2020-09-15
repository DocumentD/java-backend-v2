package de.skillkiller.documentdbackend.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.*;

public class SimpleMultipartFile implements MultipartFile {
    private final byte[] imgContent;
    private final String filename;

    public SimpleMultipartFile(byte[] imgContent, String filename) {
        this.imgContent = imgContent;
        this.filename = filename;
    }

    @Override
    public String getName() {
        return filename;
    }

    @Override
    public String getOriginalFilename() {
        return filename;
    }

    @Override
    public String getContentType() {
        return "application/x-binary";
    }

    @Override
    public boolean isEmpty() {
        return imgContent == null || imgContent.length == 0;
    }

    @Override
    public long getSize() {
        return imgContent.length;
    }

    @Override
    public byte[] getBytes() {
        return imgContent;
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(imgContent);
    }

    @Override
    public void transferTo(File dest) throws IOException, IllegalStateException {
        new FileOutputStream(dest).write(imgContent);
    }
}
