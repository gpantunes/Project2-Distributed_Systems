package tukano.api;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Blob {

    @Id
    private String blobId;
    private String fileId;

    public Blob() {

    }

    public Blob(String blobId, String fileId) {
        this.blobId = blobId;
        this.fileId = fileId;
    }

    public String getBlobId() {
        return blobId;
    }

    public String getFileId(){
        return fileId;
    }
}
