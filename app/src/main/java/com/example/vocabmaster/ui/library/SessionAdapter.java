package com.example.vocabmaster.ui.library;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.CourseScheduleDay;
import com.example.vocabmaster.databinding.ItemStudySessionBinding;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.SessionViewHolder> {
    private List<CourseScheduleDay> sessions;
    private final SimpleDateFormat dayFormat = new SimpleDateFormat("EEE", new Locale("vi", "VN"));
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd", Locale.getDefault());

    public SessionAdapter(List<CourseScheduleDay> sessions) {
        this.sessions = sessions;
    }

    public void setSessions(List<CourseScheduleDay> sessions) {
        this.sessions = sessions;
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
        
        holder.binding.textSessionDay.setText(dayFormat.format(session.getDate()).toUpperCase());
        holder.binding.textSessionDate.setText(dateFormat.format(session.getDate()));
        holder.binding.textSessionTitle.setText("Buổi học số " + (position + 1));
        
        int lessonCount = session.getLessonIds() != null ? session.getLessonIds().size() : 0;
        holder.binding.textSessionStats.setText(lessonCount + " bài học • " + session.getDailyMinutesGoal() + " phút");

        if ("completed".equals(session.getStatus())) {
            holder.binding.imageSessionStatus.setImageResource(R.drawable.ic_dot_active);
            holder.binding.imageSessionStatus.setColorFilter(holder.itemView.getContext().getColor(R.color.brand_primary));
        } else {
            holder.binding.imageSessionStatus.setImageResource(R.drawable.ic_dot_inactive);
            holder.binding.imageSessionStatus.setColorFilter(Color.LTGRAY);
        }
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
}
