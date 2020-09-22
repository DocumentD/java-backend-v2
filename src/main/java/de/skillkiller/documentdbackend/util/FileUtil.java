package de.skillkiller.documentdbackend.util;

import de.skillkiller.documentdbackend.entity.Document;
import de.skillkiller.documentdbackend.search.MeiliSearch;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Optional;

@Service
public class FileUtil {

    private final String BASE_DIR;
    private final MeiliSearch meiliSearch;

    public FileUtil(@Value("${file.basepath}") String base_dir, MeiliSearch meiliSearch) {
        BASE_DIR = base_dir;
        this.meiliSearch = meiliSearch;
    }

    public File getBaseDirFromUser(String userId) {
        return new File(BASE_DIR + userId);
    }

    public File getFile(Document document) {
        return new File(getBaseDirFromUser(document.getUserId()) + "/" + document.getFilename());
    }

    public Optional<File> getFile(String documentId) {
        Optional<Document> optionalDocument = meiliSearch.getDocumentById(documentId);
        return optionalDocument.map(this::getFile);
    }
}
