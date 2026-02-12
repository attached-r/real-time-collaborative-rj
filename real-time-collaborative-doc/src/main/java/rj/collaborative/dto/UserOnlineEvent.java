package rj.collaborative.dto;
// 事件类
public class UserOnlineEvent {

    private final String docId;
    private final String username;

    public UserOnlineEvent(String docId, String username) {
        this.docId = docId;
        this.username = username;
    }

    public String getDocId() {
        return docId;
    }

    public String getUsername() {
        return username;
    }
}