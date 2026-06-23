package com.example.vocabmaster.ui.library;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.CourseScheduleDay;
import com.example.vocabmaster.databinding.ItemLessonInSessionBinding;
import com.example.vocabmaster.databinding.ItemStudySessionBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.SessionViewHolder> {
    private List<CourseScheduleDay> sessions;
    private final SimpleDateFormat fullDateFormat = new SimpleDateFormat("EEEE, d 'Tháng' M", new Locale("vi", "VN"));
    private Map<String, LessonSessionInfo> lessonInfoMap = new HashMap<>();

    public SessionAdapter(List<CourseScheduleDay> sessions) {
        this.sessions = sessions;
    }

    public void setSessions(List<CourseScheduleDay> sessions) {
        this.sessions = sessions;
        notifyDataSetChanged();
    }

    public void setLessonInfoMap(Map<String, LessonSessionInfo> lessonInfoMap) {
        this.lessonInfoMap = lessonInfoMap == null ? new HashMap<>() : lessonInfoMap;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemStudySessionBinding binding = ItemStudySessionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new SessionViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        CourseScheduleDay session = sessions.get(position);
        
        holder.binding.textSessionBadge.setText("Buổi " + (position + 1));
        if (session.getDate() != null) {
            String dateStr = fullDateFormat.format(session.getDate());
            dateStr = dateStr.substring(0, 1).toUpperCase() + dateStr.substring(1);
            holder.binding.textSessionFullDate.setText(dateStr);
        }

        // Fix: Tính toán số thứ tự bài học bắt đầu dựa trên các buổi trước
        int startLessonNumber = 1;
        for (int j = 0; j < position; j++) {
            if (sessions.get(j).getLessonIds() != null) {
                startLessonNumber += sessions.get(j).getLessonIds().size();
            }
        }

        List<LessonSessionInfo> infos = getLessonInfos(session);
        int totalStars = 0;
        int completedLessons = 0;
        int lessonCount = session.getLessonIds() != null ? session.getLessonIds().size() : 0;
        String skillSummary = "Vocabulary";

        holder.binding.layoutLessonsContainer.removeAllViews();

        for (int i = 0; i < infos.size(); i++) {
            LessonSessionInfo info = infos.get(i);
            
            ItemLessonInSessionBinding lessonBinding = ItemLessonInSessionBinding.inflate(
                    LayoutInflater.from(holder.itemView.getContext()), holder.binding.layoutLessonsContainer, true);
            
            // Format số bài học thành 01, 02...
            lessonBinding.textLessonNumber.setText(String.format(Locale.US, "%02d", startLessonNumber + i));
            lessonBinding.textLessonTitle.setText(info.title);
            
            int stars = starsForProgress(info.progressPercent);
            totalStars += stars;
            lessonBinding.textStarsCount.setText(stars + "/3");
            
            if (info.progressPercent >= 100) {
                completedLessons++;
                lessonBinding.textLessonSubtitle.setText("1/1 Section");
                // Hiển thị trophy nếu hoàn thành xuất sắc (tùy chọn theo ảnh mẫu)
                if (stars == 3) {
                    lessonBinding.imageTrophySmall1.setVisibility(View.VISIBLE);
                    lessonBinding.imageTrophySmall1.setColorFilter(Color.parseColor("#FFD700"));
                    lessonBinding.imageTrophySmall2.setVisibility(View.VISIBLE);
                    lessonBinding.imageTrophySmall2.setColorFilter(Color.parseColor("#FFD700"));
                    lessonBinding.imageTrophySmall3.setVisibility(View.VISIBLE);
                    lessonBinding.imageTrophySmall3.setColorFilter(Color.parseColor("#FFD700"));
                }
            } else {
                lessonBinding.textLessonSubtitle.setText("0/1 Section");
            }

            if (info.skill != null && !info.skill.isEmpty()) {
                skillSummary = formatSkill(info.skill);
            }
        }

        int maxStars = Math.max(lessonCount, 1) * 3;
        holder.binding.textSessionSkill.setText(skillSummary);
        holder.binding.textSessionStars.setText(totalStars + "/" + maxStars);

        if (totalStars > 0) {
            holder.binding.imageSessionTrophy.setColorFilter(Color.parseColor("#FFD700"));
            holder.binding.textSessionStars.setTextColor(Color.parseColor("#FFA000"));
        } else {
            holder.binding.imageSessionTrophy.setColorFilter(Color.parseColor("#BDBDBD"));
            holder.binding.textSessionStars.setTextColor(Color.parseColor("#9E9E9E"));
        }

        // Cập nhật giao diện hoàn thành theo ảnh mẫu
        boolean sessionComplete = (lessonCount > 0 && completedLessons >= lessonCount) || "completed".equals(session.getStatus());
        if (sessionComplete) {
            // Card xanh lá nhạt
            holder.binding.getRoot().setCardBackgroundColor(Color.parseColor("#F1F8E9")); 
            // Badge xanh lá có dấu tích
            holder.binding.textSessionBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50")));
            holder.binding.textSessionBadge.setText("Buổi " + (position + 1) + " \u2713"); // Thêm dấu check
            
            holder.binding.imageSessionStatus.setImageResource(R.drawable.ic_check);
            holder.binding.imageSessionStatus.setColorFilter(Color.parseColor("#4CAF50"));
            
            holder.binding.textSessionMessage.setText("Đã hoàn thành buổi học này");
            holder.binding.textSessionMessage.setTextColor(Color.parseColor("#4CAF50"));
        } else {
            // Giao diện mặc định (chưa hoàn thành)
            holder.binding.getRoot().setCardBackgroundColor(Color.parseColor("#FFF8E1"));
            holder.binding.textSessionBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FF9800")));
            holder.binding.textSessionBadge.setText("Buổi " + (position + 1));
            
            holder.binding.imageSessionStatus.setImageResource(R.drawable.info);
            holder.binding.imageSessionStatus.setColorFilter(Color.parseColor("#FF9800"));
            
            holder.binding.textSessionMessage.setText("Bạn chưa hoàn thành buổi học này");
            holder.binding.textSessionMessage.setTextColor(Color.parseColor("#FFB74D"));
        }
    }

    private List<LessonSessionInfo> getLessonInfos(CourseScheduleDay session) {
        List<LessonSessionInfo> infos = new ArrayList<>();
        if (session.getLessonIds() == null) return infos;
        for (String lessonId : session.getLessonIds()) {
            LessonSessionInfo info = lessonInfoMap.get(lessonId);
            if (info != null) {
                infos.add(info);
            } else {
                infos.add(new LessonSessionInfo("Lesson " + lessonId, "vocabulary", 0, 10));
            }
        }
        return infos;
    }

    private int starsForProgress(int progressPercent) {
        if (progressPercent >= 100) return 3;
        if (progressPercent >= 75) return 2;
        if (progressPercent >= 50) return 1;
        return 0;
    }

    private String formatSkill(String skill) {
        if (skill == null || skill.trim().isEmpty()) return "Vocabulary";
        String value = skill.trim().replace("_", " ");
        return value.substring(0, 1).toUpperCase(Locale.US) + value.substring(1);
    }

    @Override
    public int getItemCount() {
        return sessions != null ? sessions.size() : 0;
    }

    static class SessionViewHolder extends RecyclerView.ViewHolder {
        final ItemStudySessionBinding binding;
        SessionViewHolder(ItemStudySessionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    public static class LessonSessionInfo {
        public final String title;
        public final String skill;
        public final int progressPercent;
        public final int xpPoints;

        public LessonSessionInfo(String title, String skill, int progressPercent, int xpPoints) {
            this.title = title == null || title.trim().isEmpty() ? "Bài học" : title;
            this.skill = skill == null || skill.trim().isEmpty() ? "vocabulary" : skill;
            this.progressPercent = Math.max(0, Math.min(100, progressPercent));
            this.xpPoints = Math.max(0, xpPoints);
        }
    }
}
