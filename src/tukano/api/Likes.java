package tukano.api;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Likes {

    @Id
    private String userId;
    @Id
    private String shortId;

    public Likes(String userId, String shortId){
        this.userId = userId;
        this.shortId = shortId;
    }

    public Likes() {}

    public String getUserId(){
        return userId;
    }

    public String getShortId(){
        return shortId;
    }

    public void setUserId(String userId){
        this.userId = userId;
    }

    public void setShortId(String shortId){
        this.shortId = shortId;
    }

}
