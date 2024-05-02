package tukano.api;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Follow {

    @Id
    private String followerId;
    @Id
    private String followedId;

    public Follow(String followerId, String followedId){
        this.followerId = followerId;
        this.followedId = followedId;
    }

    public Follow() {}

    public String getFollowerId(){
        return followerId;
    }

    public String getFollowedId(){
        return followedId;
    }

    public void setFollowerId(String followerId){
        this.followerId = followerId;
    }

    public void setFollowedId(String followedId){
        this.followedId = followedId;
    }

}
