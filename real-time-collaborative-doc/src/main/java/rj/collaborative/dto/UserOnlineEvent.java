package rj.collaborative.dto;
// 事件类
public class UserOnlineEvent {

    private final String docId;

    public UserOnlineEvent(String docId) {
        this.docId = docId;
    }

    public String getDocId() {
        return docId;
    }
}