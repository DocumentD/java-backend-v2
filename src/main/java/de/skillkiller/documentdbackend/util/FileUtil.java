package de.skillkiller.documentdbackend.util;

import de.skillkiller.documentdbackend.entity.Document;
import de.skillkiller.documentdbackend.search.MeliSearch;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Optional;

@Service
public class FileUtil {

    private final String BASE_DIR;
    private final MeliSearch meliSearch;

    public FileUtil(@Value("${file.basepath}") String base_dir, MeliSearch meliSearch) {
        BASE_DIR = base_dir;
        this.meliSearch = meliSearch;
    }

    public File getBaseDirFromUser(String userId) {
        return new File(BASE_DIR + userId);
    }

    public File getFile(Document document) {
        return new File(getBaseDirFromUser(document.getUserId()) + "/" + document.getFilename());
    }

    public Optional<File> getFile(String documentId) {
        Optional<Document> optionalDocument = meliSearch.getDocumentById(documentId);
        return optionalDocument.map(this::getFile);
    }
}
