package com.example.vocabmaster.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Topic;

import java.util.ArrayList;
import java.util.List;

public class TopicAdapter extends RecyclerView.Adapter<TopicAdapter.ViewHolder> {

    private List<Topic> topics = new ArrayList<>();
    private final OnTopicClickListener listener;
    private boolean isHorizontal = false;

    public interface OnTopicClickListener {
        void onTopicClick(Topic topic);
        void onDownloadClick(Topic topic);
    }

    public TopicAdapter(OnTopicClickListener listener) {
        this.listener = listener;
    }

    public void setTopics(List<Topic> newList) {
        this.topics = newList;
        notifyDataSetChanged();
    }

    public void setHorizontal(boolean horizontal) {
        this.isHorizontal = horizontal;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_topic_card, parent, false);
        
        if (isHorizontal) {
            ViewGroup.LayoutParams params = view.getLayoutParams();
            params.width = (parent.getMeasuredWidth() > 0) ? 
                          parent.getMeasuredWidth() / 2 : 
                          (int) (parent.getContext().getResources().getDisplayMetrics().widthPixels / 2.2); 
            view.setLayoutParams(params);
        }
        
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Topic topic = topics.get(position);
        
        // Viết hoa tên Topic
        if (topic.getName() != null) {
            holder.nameText.setText(topic.getName().toUpperCase());
        } else {
            holder.nameText.setText("");
        }
        
        // Cập nhật số từ (sử dụng trường order theo yêu cầu)
        holder.countText.setText(topic.getOrder() + " words");
        
        // Sử dụng Glide để tải hình ảnh từ URL (imageUrl)
        if (topic.getImageUrl() != null && !topic.getImageUrl().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(topic.getImageUrl())
                    .placeholder(R.drawable.more) // Ảnh hiển thị trong lúc chờ
                    .error(R.drawable.more)       // Ảnh hiển thị nếu lỗi
                    .into(holder.iconImage);
        } else {
            // Nếu không có URL, dùng hàm setTopicIcon cũ để gán icon mặc định
            setTopicIcon(holder.iconImage, topic.getName());
        }

        if (topic.isDownloaded()) {
            holder.btnDownload.setVisibility(View.GONE);
            holder.imgDownloaded.setVisibility(View.VISIBLE);
        } else {
            holder.btnDownload.setVisibility(View.VISIBLE);
            holder.imgDownloaded.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> listener.onTopicClick(topic));
        holder.btnDownload.setOnClickListener(v -> {
            if (listener != null) listener.onDownloadClick(topic);
        });
    }

    private void setTopicIcon(ImageView imageView, String name) {
        if (name == null) {
            imageView.setImageResource(R.drawable.more);
            return;
        }
        String n = name.toLowerCase();
        if (n.contains("career") || n.contains("nghề")) imageView.setImageResource(R.drawable.career);
        else if (n.contains("travel") || n.contains("du lịch")) imageView.setImageResource(R.drawable.travel);
        else if (n.contains("school") || n.contains("học")) imageView.setImageResource(R.drawable.school);
        else if (n.contains("food") || n.contains("thức ăn")) imageView.setImageResource(R.drawable.food);
        else if (n.contains("culture") || n.contains("văn hóa")) imageView.setImageResource(R.drawable.culture);
        else if (n.contains("brain") || n.contains("não")) imageView.setImageResource(R.drawable.brainskill);
        else imageView.setImageResource(R.drawable.more);
    }

    @Override
    public int getItemCount() {
        return topics.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView nameText, countText;
        ImageView iconImage, imgDownloaded, btnDownload;

        ViewHolder(View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.text_topic_name);
            countText = itemView.findViewById(R.id.text_word_count);
            iconImage = itemView.findViewById(R.id.img_topic_icon);
            imgDownloaded = itemView.findViewById(R.id.img_downloaded);
            btnDownload = itemView.findViewById(R.id.btn_download);
        }
    }
}
