package com.example.vocabmaster.ui.social;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Notification;
import com.example.vocabmaster.databinding.ActivityQrFriendScanBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class QrFriendScanActivity extends AppCompatActivity {
    private ActivityQrFriendScanBinding binding;
    private FirebaseFirestore db;
    private String currentUserId;

    private final ActivityResultLauncher<Void> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicturePreview(),
            bitmap -> {
                if (bitmap != null) {
                    binding.imgPreview.setImageBitmap(bitmap);
                    decodeAndAddFriend(bitmap);
                }
            });

    private final ActivityResultLauncher<String> imagePicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) loadQrImage(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityQrFriendScanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getUid();

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.white));
        binding.toolbar.setNavigationIconTint(ContextCompat.getColor(this, R.color.white));
        binding.btnScanCamera.setOnClickListener(v -> cameraLauncher.launch(null));
        binding.btnPickImage.setOnClickListener(v -> imagePicker.launch("image/*"));

        if (getIntent().getBooleanExtra("auto_scan", false)) {
            binding.getRoot().post(() -> cameraLauncher.launch(null));
        }
    }

    private void loadQrImage(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            binding.imgPreview.setImageBitmap(bitmap);
            decodeAndAddFriend(bitmap);
        } catch (Exception e) {
            Toast.makeText(this, "Khong doc duoc anh QR", Toast.LENGTH_SHORT).show();
        }
    }

    private void decodeAndAddFriend(Bitmap bitmap) {
        setLoading(true);
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            BinaryBitmap binaryBitmap = new BinaryBitmap(
                    new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
            Result result = new QRCodeReader().decode(binaryBitmap);
            String friendCode = extractFriendCode(result.getText());
            if (TextUtils.isEmpty(friendCode)) {
                showStatus("QR khong dung dinh dang ket ban.");
                setLoading(false);
                return;
            }
            findAndFollow(friendCode);
        } catch (Exception e) {
            showStatus("Khong nhan dien duoc QR. Hay chup ro hon hoac chon anh QR.");
            setLoading(false);
        }
    }

    private String extractFriendCode(String raw) {
        if (raw == null) return "";
        String data = raw.trim();
        if (data.matches("\\d{6}")) return data;
        Uri uri = Uri.parse(data);
        if ("vocabmaster".equals(uri.getScheme()) && "friend".equals(uri.getHost())) {
            String shortId = uri.getQueryParameter("shortId");
            if (!TextUtils.isEmpty(shortId)) return shortId;
            String uid = uri.getQueryParameter("uid");
            if (!TextUtils.isEmpty(uid)) return uid;
        }
        return data;
    }

    private void findAndFollow(String code) {
        if (currentUserId == null) {
            Toast.makeText(this, "Can dang nhap de ket ban", Toast.LENGTH_SHORT).show();
            setLoading(false);
            return;
        }

        Query query = code.matches("\\d{6}")
                ? db.collection("users").whereEqualTo("shortId", code)
                : db.collection("users").whereEqualTo("uid", code);

        query.get().addOnSuccessListener(qs -> {
            if (qs.isEmpty()) {
                db.collection("users").document(code).get().addOnSuccessListener(doc -> {
                    if (doc.exists()) followUser(doc);
                    else {
                        showStatus("Khong tim thay nguoi dung tu QR.");
                        setLoading(false);
                    }
                }).addOnFailureListener(e -> setLoading(false));
                return;
            }
            followUser(qs.getDocuments().get(0));
        }).addOnFailureListener(e -> {
            showStatus("Loi tim nguoi dung.");
            setLoading(false);
        });
    }

    private void followUser(DocumentSnapshot targetDoc) {
        String targetUid = targetDoc.getId();
        if (targetUid.equals(currentUserId)) {
            showStatus("Day la QR cua ban.");
            setLoading(false);
            return;
        }

        db.collection("users").document(currentUserId).collection("following").document(targetUid)
                .get()
                .addOnSuccessListener(followingDoc -> {
                    if (followingDoc.exists()) {
                        showStatus("Ban da theo doi nguoi dung nay.");
                        setLoading(false);
                        return;
                    }
                    createFollow(targetDoc);
                })
                .addOnFailureListener(e -> setLoading(false));
    }

    private void createFollow(DocumentSnapshot targetDoc) {
        String targetUid = targetDoc.getId();
        WriteBatch batch = db.batch();
        Map<String, Object> data = new HashMap<>();
        data.put("timestamp", FieldValue.serverTimestamp());

        batch.set(db.collection("users").document(currentUserId).collection("following").document(targetUid), data);
        batch.set(db.collection("users").document(targetUid).collection("followers").document(currentUserId), data);

        db.collection("users").document(currentUserId).collection("followers").document(targetUid)
                .get()
                .addOnSuccessListener(followerDoc -> {
                    boolean becomesFriend = followerDoc.exists();
                    if (becomesFriend) {
                        batch.update(db.collection("users").document(currentUserId), "friendsCount", FieldValue.increment(1));
                        batch.update(db.collection("users").document(targetUid), "friendsCount", FieldValue.increment(1));
                    }

                    batch.commit().addOnSuccessListener(unused -> {
                        sendNotification(targetDoc, becomesFriend);
                        showStatus(becomesFriend ? "Hai ban da tro thanh ban be!" : "Da theo doi nguoi dung tu QR.");
                        setLoading(false);
                    }).addOnFailureListener(e -> {
                        showStatus("Khong the ket ban luc nay.");
                        setLoading(false);
                    });
                })
                .addOnFailureListener(e -> setLoading(false));
    }

    private void sendNotification(DocumentSnapshot targetDoc, boolean friendship) {
        db.collection("users").document(currentUserId).get().addOnSuccessListener(me -> {
            String name = me.getString("name");
            if (TextUtils.isEmpty(name)) name = "Nguoi dung";
            Notification notification = new Notification(
                    friendship ? "friend" : "follow",
                    friendship ? "Ban be moi" : "Nguoi theo doi moi",
                    friendship ? "Ban va " + name + " da tro thanh ban be." : name + " da bat dau theo doi ban.",
                    currentUserId,
                    name
            );
            notification.setFromUserAvatar(me.getString("avatar"));
            db.collection("users").document(targetDoc.getId())
                    .collection("notifications")
                    .add(notification);
        });
    }

    private void showStatus(String status) {
        binding.textStatus.setText(status);
        Toast.makeText(this, status, Toast.LENGTH_SHORT).show();
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnScanCamera.setEnabled(!loading);
        binding.btnPickImage.setEnabled(!loading);
    }
}
