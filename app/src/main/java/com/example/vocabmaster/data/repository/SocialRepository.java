package com.example.vocabmaster.data.repository;

import com.example.vocabmaster.data.model.Comment;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import java.util.HashMap;
import java.util.Map;

public class SocialRepository {
    private final FirebaseFirestore db;
    private final CollectionReference postsRef;

    public SocialRepository() {
        db = FirebaseFirestore.getInstance();
        postsRef = db.collection("posts");
    }

    public Task<Void> toggleLike(String postId, boolean isLiked) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (isLiked) {
            return postsRef.document(postId).update("likes", FieldValue.arrayUnion(uid));
        } else {
            return postsRef.document(postId).update("likes", FieldValue.arrayRemove(uid));
        }
    }

    public Task<Void> addComment(String postId, String content, String userName, String userAvatar) {
        String uid = FirebaseAuth.getInstance().getUid();
        Map<String, Object> comment = new HashMap<>();
        comment.put("userId", uid);
        comment.put("userName", userName);
        comment.put("userAvatar", userAvatar);
        comment.put("content", content);
        comment.put("createdAt", FieldValue.serverTimestamp());

        return postsRef.document(postId).collection("comments").add(comment)
                .continueWithTask(task -> postsRef.document(postId).update("commentCount", FieldValue.increment(1)));
    }

    public Query getCommentsQuery(String postId) {
        return postsRef.document(postId).collection("comments").orderBy("createdAt", Query.Direction.ASCENDING);
    }
}
