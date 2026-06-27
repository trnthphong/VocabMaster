package com.example.vocabmaster.ui.admin;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.api.RetrofitClient;
import com.example.vocabmaster.data.remote.VocabMasterApiService;
import com.example.vocabmaster.ui.auth.LoginActivity;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminActivity extends AppCompatActivity {
    private static final String SUPER_ADMIN_EMAIL = "23521406@gm.uit.edu.vn";
    private FirebaseFirestore db;
    private VocabMasterApiService apiService;
    private LinearLayout content;
    private ProgressBar progress;
    private TabLayout tabs;
    private String currentTab = "dashboard";
    private String currentAdminRole = "";
    private final List<DocumentSnapshot> cachedUsers = new ArrayList<>();
    private final List<DocumentSnapshot> cachedTopics = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = FirebaseFirestore.getInstance();
        apiService = RetrofitClient.getClient().create(VocabMasterApiService.class);
        buildShell();
        verifyAdminAndLoad();
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.surface_light));
        setContentView(root);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Admin");
        toolbar.setTitleTextColor(Color.WHITE);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationIconTint(Color.WHITE);
        toolbar.setBackgroundColor(getColor(R.color.brand_primary));
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.getMenu().add("Đăng xuất")
                .setIcon(R.drawable.ic_admin_logout)
                .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
        toolbar.setOnMenuItemClickListener(item -> {
            confirmAction("Xác nhận đăng xuất", "Bạn có muốn đăng xuất khỏi tài khoản admin không?", this::logout);
            return true;
        });
        root.addView(toolbar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
        ));

        tabs = new TabLayout(this);
        tabs.setBackgroundColor(Color.WHITE);
        tabs.addTab(tabs.newTab().setText("Dashboard").setTag("dashboard"));
        tabs.addTab(tabs.newTab().setText("Users").setTag("users"));
        tabs.addTab(tabs.newTab().setText("Reports").setTag("reports"));
        tabs.addTab(tabs.newTab().setText("Bộ từ").setTag("topics"));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                currentTab = String.valueOf(tab.getTag());
                renderCurrentTab();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) { renderCurrentTab(); }
        });
        root.addView(tabs, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(4)
        ));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
    }

    private void verifyAdminAndLoad() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            finish();
            return;
        }
        String uid = firebaseUser.getUid();
        showLoading(true);
        db.collection("users").document(uid).get()
                .addOnSuccessListener(snapshot -> {
                    showLoading(false);
                    String role = snapshot.getString("role");
                    if (isSeedSuperAdmin(firebaseUser) && !"super_admin".equalsIgnoreCase(role)) {
                        bootstrapSuperAdmin();
                        return;
                    }
                    if (!isAdminRole(role)) {
                        Toast.makeText(this, "Bạn không có quyền truy cập Admin", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    currentAdminRole = role;
                    renderCurrentTab();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    if (isSeedSuperAdmin(firebaseUser)) {
                        bootstrapSuperAdmin();
                        return;
                    }
                    Toast.makeText(this, "Không kiểm tra được quyền Admin", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void bootstrapSuperAdmin() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            finish();
            return;
        }

        showLoading(true);
        firebaseUser.getIdToken(true)
                .addOnSuccessListener(token -> apiService.bootstrapSuperAdmin("Bearer " + token.getToken())
                        .enqueue(new Callback<Map<String, Object>>() {
                            @Override
                            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                                showLoading(false);
                                if (!response.isSuccessful()) {
                                    toast("Không kích hoạt được quyền Super Admin");
                                    finish();
                                    return;
                                }
                                currentAdminRole = "super_admin";
                                toast("Đã kích hoạt Super Admin");
                                renderCurrentTab();
                            }

                            @Override
                            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                                showLoading(false);
                                toast("Không kết nối được backend Admin");
                                finish();
                            }
                        }))
                .addOnFailureListener(e -> {
                    showLoading(false);
                    toast("Không lấy được token đăng nhập");
                    finish();
                });
    }

    private void logout() {
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void renderCurrentTab() {
        content.removeAllViews();
        if ("users".equals(currentTab)) loadUsers("");
        else if ("reports".equals(currentTab)) loadReports();
        else if ("topics".equals(currentTab)) loadTopics();
        else renderDashboard();
    }

    private void renderDashboard() {
        addTitle("Dashboard Admin");
        loadUserDashboard();
        loadContentDashboard();
    }

    private void loadUserDashboard() {
        db.collection("users").get().addOnSuccessListener(snapshot -> {
            int total = snapshot.size();
            int premium = 0;
            int banned = 0;
            int normal = 0;
            for (DocumentSnapshot user : snapshot.getDocuments()) {
                boolean isPremium = bool(user, "premium") || bool(user, "isPremium");
                boolean isBanned = bool(user, "banned") || bool(user, "disabled")
                        || "banned".equalsIgnoreCase(user.getString("accountStatus"));
                if (isPremium) premium++;
                if (isBanned) banned++;
                if (!isPremium && !isBanned) normal++;
            }

            LinearLayout card = chartCard("Người dùng hệ thống", total + " tài khoản");
            card.addView(userPieChart(normal, premium, banned));
            card.addView(legendRow("Thường", normal, getColor(R.color.info)));
            card.addView(legendRow("Premium", premium, getColor(R.color.warning)));
            card.addView(legendRow("Bị khóa", banned, getColor(R.color.error)));
            content.addView(card, 1);
        });
    }

    private void loadContentDashboard() {
        db.collection("reports").whereEqualTo("status", "pending").get()
                .addOnSuccessListener(reports -> content.addView(statCard("Report chờ duyệt", String.valueOf(reports.size()))));

        db.collection("topics").get()
                .addOnSuccessListener(topics -> content.addView(statCard("Bộ từ hệ thống", String.valueOf(topics.size()))));
    }

    private void selectTab(String tag) {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            TabLayout.Tab tab = tabs.getTabAt(i);
            if (tab != null && tag.equals(tab.getTag())) {
                tab.select();
                return;
            }
        }
    }

    private void loadCount(LinearLayout target, String label, String collection) {
        db.collection(collection).get().addOnSuccessListener(snapshot ->
                target.addView(statCard(label, String.valueOf(snapshot.size()))));
    }

    private void loadPendingReportCount(LinearLayout target) {
        db.collection("reports").whereEqualTo("status", "pending").get()
                .addOnSuccessListener(snapshot -> target.addView(statCard("Report chờ duyệt", String.valueOf(snapshot.size()))));
    }

    private void loadUsers(String query) {
        addTitle("Tất cả người dùng");
        EditText search = searchBox("Tìm tên, email, short ID...");
        content.addView(search);
        search.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                renderUserList(v.getText().toString().trim());
                return true;
            }
            return false;
        });

        MaterialButton searchButton = viewButton("Tìm kiếm");
        searchButton.setOnClickListener(v -> renderUserList(search.getText().toString().trim()));
        content.addView(searchButton, fullButtonParams());

        showLoading(true);
        db.collection("users").get()
                .addOnSuccessListener(snapshot -> {
                    showLoading(false);
                    cachedUsers.clear();
                    cachedUsers.addAll(snapshot.getDocuments());
                    renderUserList(query);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    toast("Không tải được users");
                });
    }

    private void renderUserList(String query) {
        while (content.getChildCount() > 3) content.removeViewAt(3);
        String normalized = query.toLowerCase(Locale.US);
        for (DocumentSnapshot user : cachedUsers) {
            String haystack = (safe(user.getString("name")) + " "
                    + safe(user.getString("email")) + " "
                    + safe(user.getString("shortId"))).toLowerCase(Locale.US);
            if (!normalized.isEmpty() && !haystack.contains(normalized)) continue;
            content.addView(userCard(user));
        }
    }

    private View userCard(DocumentSnapshot user) {
        LinearLayout card = card();
        String uid = user.getId();
        String name = safe(user.getString("name"));
        String email = safe(user.getString("email"));
        String role = safe(user.getString("role"));
        boolean premium = bool(user, "premium") || bool(user, "isPremium");
        boolean banned = bool(user, "banned") || bool(user, "disabled");
        boolean adminRole = isAdminRole(role);
        boolean superAdminRole = "super_admin".equalsIgnoreCase(role);

        TextView title = text(name.isEmpty() ? "Người dùng" : name, 17, R.color.text_primary, true);
        card.addView(title);
        card.addView(text(email, 14, R.color.text_secondary, false));
        card.addView(text("UID: " + uid + "\nRole: " + (role.isEmpty() ? "user" : role)
                + "\nPremium: " + (premium ? "Có" : "Không")
                + "\nTrạng thái: " + (banned ? "Đã khóa" : "Hoạt động"), 13, R.color.text_secondary, false));

        LinearLayout actions = row();
        MaterialButton premiumBtn = premium ? deleteButton("Gỡ Premium") : viewButton("Cấp Premium");
        premiumBtn.setOnClickListener(v -> confirmAction(
                premium ? "Gỡ Premium?" : "Cấp Premium?",
                premium
                        ? "Tài khoản này sẽ mất quyền Premium thủ công."
                        : "Tài khoản này sẽ được cấp Premium thủ công 365 ngày.",
                () -> setPremium(uid, !premium)
        ));
        actions.addView(premiumBtn, weightParams());

        MaterialButton banBtn = banned ? viewButton("Mở khóa") : deleteButton("Khóa");
        banBtn.setOnClickListener(v -> confirmAction(
                banned ? "Mở khóa tài khoản?" : "Khóa tài khoản?",
                banned
                        ? "Người dùng này sẽ có thể đăng nhập lại."
                        : "Người dùng này sẽ không thể đăng nhập vào ứng dụng.",
                () -> setBanned(uid, !banned)
        ));
        actions.addView(banBtn, weightParams());
        card.addView(actions);

        if (isCurrentSuperAdmin()) {
            if (superAdminRole) {
                card.addView(text("Quyền: Super Admin gốc, không thể gỡ trong app.", 13, R.color.brand_primary, true));
            } else {
                MaterialButton roleButton = adminRole ? deleteButton("Gỡ Admin") : viewButton("Cấp Admin");
                roleButton.setOnClickListener(v -> confirmAction(
                        adminRole ? "Gỡ quyền Admin?" : "Cấp quyền Admin?",
                        adminRole
                                ? "Tài khoản này sẽ trở về quyền người dùng thường."
                                : "Tài khoản này sẽ được vào giao diện Admin để quản lý hệ thống.",
                        () -> updateUserRole(uid, adminRole ? "user" : "admin")
                ));
                card.addView(roleButton, fullButtonParams());
            }
        }

        MaterialButton learning = viewButton("Tóm tắt học tập");
        learning.setOnClickListener(v -> showLearningSummary(user));
        card.addView(learning, fullButtonParams());
        return card;
    }

    private void showLearningSummary(DocumentSnapshot user) {
        LinearLayout body = dialogForm();
        body.addView(text("Đang tải dữ liệu học tập...", 15, R.color.text_secondary, false));
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Tình trạng học tập")
                .setView(body)
                .setPositiveButton("Đóng", null)
                .show();

        String uid = user.getId();
        long xp = number(user, "xp", 0);
        long streak = number(user, "streak", 0);
        long longestStreak = number(user, "longestStreak", 0);
        Timestamp lastActive = user.getTimestamp("lastActive");
        String activeCourse = safe(user.getString("activeCourseId"));
        String currentUnit = safe(user.getString("currentUnitTitle"));

        db.collection("user_progress").whereEqualTo("userId", uid).get()
                .addOnSuccessListener(progressSnapshot -> {
                    int reviewed = progressSnapshot.size();
                    int learned = 0;
                    int learning = 0;
                    Timestamp latestReview = null;
                    for (DocumentSnapshot progressDoc : progressSnapshot.getDocuments()) {
                        String status = safe(progressDoc.getString("status"));
                        if ("learned".equalsIgnoreCase(status) || "mastered".equalsIgnoreCase(status)) learned++;
                        else if (!status.isEmpty()) learning++;
                        Timestamp lastReviewed = progressDoc.getTimestamp("lastReviewed");
                        if (lastReviewed != null && (latestReview == null
                                || lastReviewed.toDate().after(latestReview.toDate()))) {
                            latestReview = lastReviewed;
                        }
                    }

                    body.removeAllViews();
                    body.addView(statCard("XP", String.valueOf(xp)));
                    body.addView(statCard("Streak hiện tại", streak + " ngày"));
                    body.addView(statCard("Streak dài nhất", longestStreak + " ngày"));
                    body.addView(text("Lần hoạt động gần nhất: " + formatDate(lastActive)
                            + "\nLần ôn gần nhất: " + formatDate(latestReview)
                            + "\nFlashcard đã có tiến độ: " + reviewed
                            + "\nĐã học/mastered: " + learned
                            + "\nĐang học: " + learning
                            + "\nKhóa đang học: " + (activeCourse.isEmpty() ? "--" : activeCourse)
                            + "\nUnit hiện tại: " + (currentUnit.isEmpty() ? "--" : currentUnit)
                            + "\nĐánh giá: " + learningHealthLabel(streak, latestReview != null ? latestReview : lastActive),
                            14, R.color.text_primary, false));
                })
                .addOnFailureListener(e -> {
                    body.removeAllViews();
                    body.addView(text("Không tải được user_progress. Vẫn có thể xem nhanh:\nXP: " + xp
                            + "\nStreak: " + streak
                            + "\nLast active: " + formatDate(lastActive),
                            14, R.color.text_primary, false));
                });
    }

    private String learningHealthLabel(long streak, @Nullable Timestamp lastActive) {
        if (lastActive == null) return "Chưa có hoạt động gần đây";
        long days = Math.max(0, (System.currentTimeMillis() - lastActive.toDate().getTime()) / (24L * 60 * 60 * 1000));
        if (days <= 1 && streak >= 3) return "Học rất đều";
        if (days <= 3) return "Có học gần đây";
        if (days <= 7) return "Có dấu hiệu giảm nhịp";
        return "Ít hoạt động";
    }

    private void setPremium(String uid, boolean enabled) {
        Map<String, Object> update = new HashMap<>();
        update.put("premium", enabled);
        update.put("isPremium", enabled);
        update.put("premiumStatus", enabled ? "ACTIVE" : "CANCELLED");
        update.put("premiumPlanType", enabled ? "Manual Admin" : FieldValue.delete());
        update.put("premiumDays", enabled ? 365 : FieldValue.delete());
        update.put("premiumRegDate", enabled ? FieldValue.serverTimestamp() : FieldValue.delete());
        update.put("premiumUntil", enabled ? new Timestamp(new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000)) : FieldValue.delete());
        db.collection("users").document(uid).update(update)
                .addOnSuccessListener(v -> {
                    toast(enabled ? "Đã cấp Premium" : "Đã gỡ Premium");
                    loadUsers("");
                })
                .addOnFailureListener(e -> toast("Không cập nhật được Premium"));
    }

    private void setBanned(String uid, boolean banned) {
        Map<String, Object> update = new HashMap<>();
        update.put("banned", banned);
        update.put("disabled", banned);
        update.put("accountStatus", banned ? "banned" : "active");
        update.put("bannedAt", banned ? FieldValue.serverTimestamp() : FieldValue.delete());
        update.put("bannedBy", banned ? FirebaseAuth.getInstance().getUid() : FieldValue.delete());
        db.collection("users").document(uid).update(update)
                .addOnSuccessListener(v -> {
                    toast(banned ? "Đã khóa tài khoản" : "Đã mở khóa tài khoản");
                    loadUsers("");
                })
                .addOnFailureListener(e -> toast("Không cập nhật được tài khoản"));
    }

    private void updateUserRole(String uid, String role) {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            toast("Bạn cần đăng nhập lại");
            return;
        }

        showLoading(true);
        firebaseUser.getIdToken(true)
                .addOnSuccessListener(token -> {
                    Map<String, String> body = new HashMap<>();
                    body.put("role", role);
                    apiService.updateUserRole("Bearer " + token.getToken(), uid, body)
                            .enqueue(new Callback<Map<String, Object>>() {
                                @Override
                                public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                                    showLoading(false);
                                    if (!response.isSuccessful()) {
                                        toast("Không cập nhật được quyền");
                                        return;
                                    }
                                    toast("Đã cập nhật quyền tài khoản");
                                    loadUsers("");
                                }

                                @Override
                                public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                                    showLoading(false);
                                    toast("Không kết nối được backend Admin");
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    toast("Không lấy được token đăng nhập");
                });
    }

    private void loadReports() {
        addTitle("Report chờ duyệt");
        showLoading(true);
        db.collection("reports").whereEqualTo("status", "pending").get()
                .addOnSuccessListener(snapshot -> {
                    showLoading(false);
                    if (snapshot.isEmpty()) {
                        content.addView(emptyText("Không có report chờ duyệt."));
                        return;
                    }
                    for (DocumentSnapshot report : snapshot.getDocuments()) {
                        content.addView(reportCard(report));
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    toast("Không tải được report");
                });
    }

    private View reportCard(DocumentSnapshot report) {
        LinearLayout card = card();
        String reason = safe(report.getString("reason"));
        String targetId = safe(report.getString("targetId"));
        String targetTitle = safe(report.getString("targetTitle"));
        String targetType = safe(report.getString("targetType"));
        String reporterId = safe(report.getString("reporterId"));
        Timestamp createdAt = report.getTimestamp("createdAt");

        card.addView(text(reason.toUpperCase(Locale.US), 16, R.color.error, true));
        card.addView(text("Nội dung: " + (targetTitle.isEmpty() ? targetId : targetTitle)
                + "\nLoại: " + targetType
                + "\nReporter: " + reporterId
                + "\nNgày: " + formatDate(createdAt), 13, R.color.text_secondary, false));

        LinearLayout actions = row();
        MaterialButton resolve = viewButton("Đã xử lý");
        resolve.setOnClickListener(v -> resolveReport(report.getId(), "resolved", false, targetType, targetId));
        actions.addView(resolve, weightParams());

        MaterialButton hide = deleteButton("Ẩn nội dung");
        hide.setOnClickListener(v -> resolveReport(report.getId(), "resolved", true, targetType, targetId));
        actions.addView(hide, weightParams());

        MaterialButton dismiss = editButton("Bỏ qua");
        dismiss.setOnClickListener(v -> resolveReport(report.getId(), "dismissed", false, targetType, targetId));
        actions.addView(dismiss, weightParams());
        card.addView(actions);
        return card;
    }

    private void resolveReport(String reportId, String status, boolean hideTarget, String targetType, String targetId) {
        WriteBatch batch = db.batch();
        DocumentReference reportRef = db.collection("reports").document(reportId);
        Map<String, Object> reportUpdate = new HashMap<>();
        reportUpdate.put("status", status);
        reportUpdate.put("resolvedAt", FieldValue.serverTimestamp());
        reportUpdate.put("resolvedBy", FirebaseAuth.getInstance().getUid());
        batch.update(reportRef, reportUpdate);
        if (hideTarget && "topic".equals(targetType) && !targetId.isEmpty()) {
            batch.update(db.collection("topics").document(targetId),
                    "hidden", true,
                    "moderationStatus", "hidden",
                    "hiddenAt", FieldValue.serverTimestamp());
        }
        batch.commit()
                .addOnSuccessListener(v -> {
                    toast("Đã cập nhật report");
                    loadReports();
                })
                .addOnFailureListener(e -> toast("Không cập nhật được report"));
    }

    private void loadTopics() {
        addTitle("Quản lý bộ từ hệ thống");
        MaterialButton add = viewButton("Thêm bộ từ");
        add.setOnClickListener(v -> showTopicDialog(null));
        content.addView(add, fullButtonParams());

        showLoading(true);
        db.collection("topics").get()
                .addOnSuccessListener(snapshot -> {
                    showLoading(false);
                    cachedTopics.clear();
                    cachedTopics.addAll(snapshot.getDocuments());
                    renderTopicList();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    toast("Không tải được bộ từ");
                });
    }

    private void renderTopicList() {
        while (content.getChildCount() > 2) content.removeViewAt(2);
        for (DocumentSnapshot topic : cachedTopics) {
            content.addView(topicCard(topic));
        }
    }

    private View topicCard(DocumentSnapshot topic) {
        LinearLayout card = card();
        String topicId = topic.getId();
        String name = safe(topic.getString("name"));
        long count = number(topic, "word_count", number(topic, "order", 0));
        boolean hidden = bool(topic, "hidden");
        card.addView(text(name.isEmpty() ? topicId : name, 17, R.color.text_primary, true));
        card.addView(text("ID: " + topicId
                        + "\nSố từ: " + count
                        + "\nTrạng thái: " + (hidden ? "Đang ẩn" : "Hiển thị"),
                13, R.color.text_secondary, false));

        LinearLayout actions = row();
        MaterialButton words = viewButton("Từ vựng");
        words.setOnClickListener(v -> showWordsDialog(topic));
        actions.addView(words, weightParams());

        MaterialButton edit = editButton("Sửa");
        edit.setOnClickListener(v -> showTopicDialog(topic));
        actions.addView(edit, weightParams());

        MaterialButton delete = deleteButton("Xóa");
        delete.setOnClickListener(v -> confirmDeleteTopic(topicId));
        actions.addView(delete, weightParams());
        card.addView(actions);
        return card;
    }

    private void showTopicDialog(@Nullable DocumentSnapshot topic) {
        LinearLayout form = dialogForm();
        EditText idInput = input("ID bộ từ, ví dụ: travel");
        EditText nameInput = input("Tên bộ từ");
        EditText descInput = input("Mô tả");
        EditText imageInput = input("Image URL");
        form.addView(idInput);
        form.addView(nameInput);
        form.addView(descInput);
        form.addView(imageInput);

        boolean editing = topic != null;
        if (editing) {
            idInput.setText(topic.getId());
            idInput.setEnabled(false);
            nameInput.setText(safe(topic.getString("name")));
            descInput.setText(safe(topic.getString("description")));
            imageInput.setText(safe(topic.getString("imageUrl")));
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(editing ? "Cập nhật bộ từ" : "Thêm bộ từ")
                .setView(form)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Lưu", (dialog, which) -> {
                    String topicId = idInput.getText().toString().trim();
                    String name = nameInput.getText().toString().trim();
                    if (topicId.isEmpty() || name.isEmpty()) {
                        toast("Cần nhập ID và tên bộ từ");
                        return;
                    }
                    Map<String, Object> data = new HashMap<>();
                    data.put("name", name);
                    data.put("description", descInput.getText().toString().trim());
                    data.put("imageUrl", imageInput.getText().toString().trim());
                    data.put("hidden", false);
                    if (!editing) {
                        data.put("word_count", 0);
                        data.put("createdAt", FieldValue.serverTimestamp());
                    }
                    db.collection("topics").document(topicId).set(data, com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener(v -> loadTopics())
                            .addOnFailureListener(e -> toast("Không lưu được bộ từ"));
                })
                .show();
    }

    private void confirmDeleteTopic(String topicId) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Xóa bộ từ?")
                .setMessage("Bộ từ hệ thống và các từ bên trong sẽ bị xóa.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (dialog, which) -> deleteTopic(topicId))
                .show();
    }

    private void deleteTopic(String topicId) {
        db.collection("topics").document(topicId).collection("vocabularies").get()
                .addOnSuccessListener(words -> {
                    WriteBatch batch = db.batch();
                    for (QueryDocumentSnapshot word : words) batch.delete(word.getReference());
                    batch.delete(db.collection("topics").document(topicId));
                    batch.commit()
                            .addOnSuccessListener(v -> loadTopics())
                            .addOnFailureListener(e -> toast("Không xóa được bộ từ"));
                });
    }

    private void showWordsDialog(DocumentSnapshot topic) {
        String topicId = topic.getId();
        LinearLayout root = dialogForm();
        MaterialButton add = viewButton("Thêm từ");
        root.addView(add, fullButtonParams());

        ScrollView wordScroll = new ScrollView(this);
        wordScroll.setFillViewport(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        wordScroll.addView(list, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        root.addView(wordScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(420)
        ));

        add.setOnClickListener(v -> showWordDialog(topicId, null));
        loadWordsInto(topicId, list);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Từ vựng: " + safe(topic.getString("name")))
                .setView(root)
                .setPositiveButton("Đóng", null)
                .show();
    }

    private void loadWordsInto(String topicId, LinearLayout list) {
        list.removeAllViews();
        db.collection("topics").document(topicId).collection("vocabularies").get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        list.addView(emptyText("Chưa có từ vựng."));
                        return;
                    }
                    for (DocumentSnapshot word : snapshot.getDocuments()) {
                        list.addView(wordRow(topicId, word, list));
                    }
                })
                .addOnFailureListener(e -> list.addView(emptyText("Không tải được từ vựng.")));
    }

    private View wordRow(String topicId, DocumentSnapshot word, LinearLayout list) {
        LinearLayout card = card();
        card.addView(text(safe(word.getString("word")), 16, R.color.text_primary, true));
        card.addView(text(safe(word.getString("definition")) + "\nVI: " + safe(word.getString("vietnamese_translation")),
                13, R.color.text_secondary, false));
        LinearLayout actions = row();
        MaterialButton edit = editButton("Sửa");
        edit.setOnClickListener(v -> showWordDialog(topicId, word));
        actions.addView(edit, weightParams());
        MaterialButton delete = deleteButton("Xóa");
        delete.setOnClickListener(v -> confirmAction(
                "Xóa từ vựng?",
                "Từ \"" + safe(word.getString("word")) + "\" sẽ bị xóa khỏi bộ từ này.",
                () -> db.collection("topics").document(topicId)
                        .collection("vocabularies").document(word.getId()).delete()
                        .addOnSuccessListener(done -> {
                            refreshTopicWordCount(topicId);
                            loadWordsInto(topicId, list);
                        })
        ));
        actions.addView(delete, weightParams());
        card.addView(actions);
        return card;
    }

    private void showWordDialog(String topicId, @Nullable DocumentSnapshot word) {
        LinearLayout form = dialogForm();
        EditText wordInput = input("Word");
        EditText posInput = input("Part of speech");
        EditText defInput = input("Definition");
        EditText viInput = input("Vietnamese meaning");
        EditText exampleInput = input("Example sentence");
        form.addView(wordInput);
        form.addView(posInput);
        form.addView(defInput);
        form.addView(viInput);
        form.addView(exampleInput);

        boolean editing = word != null;
        if (editing) {
            wordInput.setText(safe(word.getString("word")));
            posInput.setText(safe(word.getString("part_of_speech")));
            defInput.setText(safe(word.getString("definition")));
            viInput.setText(safe(word.getString("vietnamese_translation")));
            exampleInput.setText(safe(word.getString("example_sentence")));
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(editing ? "Cập nhật từ" : "Thêm từ")
                .setView(form)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Lưu", (dialog, which) -> {
                    String value = wordInput.getText().toString().trim();
                    if (value.isEmpty()) {
                        toast("Cần nhập từ");
                        return;
                    }
                    Map<String, Object> data = new HashMap<>();
                    data.put("word", value);
                    data.put("part_of_speech", posInput.getText().toString().trim());
                    data.put("definition", defInput.getText().toString().trim());
                    data.put("vietnamese_translation", viInput.getText().toString().trim());
                    data.put("example_sentence", exampleInput.getText().toString().trim());
                    data.put("topic", topicId.toLowerCase(Locale.US));
                    data.put("updatedAt", FieldValue.serverTimestamp());
                    DocumentReference ref = editing
                            ? db.collection("topics").document(topicId).collection("vocabularies").document(word.getId())
                            : db.collection("topics").document(topicId).collection("vocabularies").document();
                    ref.set(data, com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener(v -> {
                                refreshTopicWordCount(topicId);
                                toast("Đã lưu từ vựng");
                            })
                            .addOnFailureListener(e -> toast("Không lưu được từ"));
                })
                .show();
    }

    private void refreshTopicWordCount(String topicId) {
        db.collection("topics").document(topicId).collection("vocabularies").get()
                .addOnSuccessListener(snapshot -> db.collection("topics").document(topicId)
                        .update("word_count", snapshot.size(), "order", snapshot.size()));
    }

    private void addTitle(String title) {
        TextView view = text(title, 24, R.color.text_primary, true);
        view.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(14));
        content.addView(view, params);
    }

    private void addSection(String title) {
        TextView view = text(title, 17, R.color.text_primary, true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(18), 0, dp(8));
        content.addView(view, params);
    }

    private View statCard(String title, String value) {
        LinearLayout card = card();
        card.addView(text(title, 14, R.color.text_secondary, false));
        card.addView(text(value, 28, R.color.brand_primary, true));
        return card;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(round(Color.WHITE, dp(12), getColor(R.color.card_border)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, 0);
        return row;
    }

    private TextView text(String value, int sp, int colorRes, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(getColor(colorRes));
        text.setLineSpacing(dp(2), 1f);
        if (bold) text.setTypeface(Typeface.DEFAULT_BOLD);
        return text;
    }

    private TextView emptyText(String value) {
        TextView empty = text(value, 15, R.color.text_secondary, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, dp(24), 0, dp(24));
        return empty;
    }

    private MaterialButton button(String text) {
        MaterialButton button = new MaterialButton(this);
        button.setText(text);
        button.setCornerRadius(dp(10));
        return button;
    }

    private MaterialButton smallButton(String text) {
        MaterialButton button = button(text);
        button.setTextSize(12);
        button.setMinHeight(dp(42));
        return button;
    }

    private MaterialButton viewButton(String text) {
        MaterialButton button = smallButton(text);
        tintButton(button, getColor(R.color.success), Color.WHITE);
        return button;
    }

    private MaterialButton editButton(String text) {
        MaterialButton button = smallButton(text);
        tintButton(button, getColor(R.color.warning), Color.WHITE);
        return button;
    }

    private MaterialButton deleteButton(String text) {
        MaterialButton button = smallButton(text);
        tintButton(button, getColor(R.color.error), Color.WHITE);
        return button;
    }

    private void tintButton(MaterialButton button, int backgroundColor, int textColor) {
        button.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
        button.setTextColor(textColor);
        button.setStrokeWidth(0);
    }

    private void confirmAction(String title, String message, Runnable action) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Huỷ", null)
                .setPositiveButton("Xác nhận", (dialog, which) -> action.run())
                .show();
    }

    private LinearLayout.LayoutParams fullButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
        );
        params.setMargins(0, dp(6), 0, dp(8));
        return params;
    }

    private LinearLayout.LayoutParams weightParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private EditText searchBox(String hint) {
        EditText input = input(hint);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        return input;
    }

    private EditText input(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextColor(getColor(R.color.text_primary));
        input.setHintTextColor(getColor(R.color.text_secondary));
        input.setSingleLine(false);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        input.setBackground(round(Color.WHITE, dp(10), getColor(R.color.card_border)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(6), 0, dp(6));
        input.setLayoutParams(params);
        return input;
    }

    private LinearLayout dialogForm() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(8), dp(8), dp(8), 0);
        return form;
    }

    private GradientDrawable round(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private GradientDrawable makeRoundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private LinearLayout chartCard(String title, String subtitle) {
        LinearLayout card = card();
        card.addView(text(title, 17, R.color.text_primary, true));
        card.addView(text(subtitle, 13, R.color.text_secondary, false));
        return card;
    }

    private View userPieChart(int normal, int premium, int banned) {
        PieChart chart = new PieChart(this);
        List<PieEntry> entries = new ArrayList<>();
        if (normal > 0) entries.add(new PieEntry(normal, "Thường"));
        if (premium > 0) entries.add(new PieEntry(premium, "Premium"));
        if (banned > 0) entries.add(new PieEntry(banned, "Bị khóa"));
        if (entries.isEmpty()) entries.add(new PieEntry(1, "Chưa có dữ liệu"));

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(getColor(R.color.info), getColor(R.color.warning), getColor(R.color.error));
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(12f);
        dataSet.setSliceSpace(3f);

        chart.setData(new PieData(dataSet));
        chart.setUsePercentValues(false);
        chart.setDrawHoleEnabled(true);
        chart.setHoleRadius(58f);
        chart.setTransparentCircleRadius(62f);
        chart.setCenterText("Users");
        chart.setCenterTextSize(15f);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.animateY(700);
        chart.invalidate();

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(190)
        );
        params.setMargins(0, dp(10), 0, dp(6));
        chart.setLayoutParams(params);
        return chart;
    }

    private View legendRow(String label, int value, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, 0);

        TextView dot = new TextView(this);
        dot.setText("");
        dot.setBackground(round(color, dp(6), color));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(12), dp(12));
        dotParams.setMargins(0, 0, dp(8), 0);
        row.addView(dot, dotParams);

        TextView text = text(label + ": " + value, 14, R.color.text_primary, false);
        row.addView(text, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return row;
    }

    private void showLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean bool(DocumentSnapshot doc, String field) {
        Boolean value = doc.getBoolean(field);
        return value != null && value;
    }

    private boolean isAdminRole(String role) {
        return "admin".equalsIgnoreCase(role) || "super_admin".equalsIgnoreCase(role);
    }

    private boolean isCurrentSuperAdmin() {
        return "super_admin".equalsIgnoreCase(currentAdminRole);
    }

    private boolean isSeedSuperAdmin(FirebaseUser user) {
        return user.getEmail() != null && SUPER_ADMIN_EMAIL.equalsIgnoreCase(user.getEmail().trim());
    }

    private long number(DocumentSnapshot doc, String field, long fallback) {
        Number value = (Number) doc.get(field);
        return value != null ? value.longValue() : fallback;
    }

    private String formatDate(@Nullable Timestamp timestamp) {
        if (timestamp == null) return "--";
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("vi", "VN")).format(timestamp.toDate());
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
