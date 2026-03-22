package com.example.vocabmaster.data.model;

import com.google.firebase.Timestamp;
import java.util.List;

public class Leaderboard {
    private String leaderboardId; // "global", "weekly_YYYY_WW"
    private String type; // "global", "weekly"
    private Timestamp startDate;
    private Timestamp endDate;
    private List<RankEntry> ranking;

    public static class RankEntry {
        private String userId;
        private String name;
        private String avatarUrl;
        private long xp;

        public RankEntry() {}

        // Getters and Setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
        public long getXp() { return xp; }
        public void setXp(long xp) { this.xp = xp; }
    }

    public Leaderboard() {}

    // Getters and Setters
    public String getLeaderboardId() { return leaderboardId; }
    public void setLeaderboardId(String leaderboardId) { this.leaderboardId = leaderboardId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Timestamp getStartDate() { return startDate; }
    public void setStartDate(Timestamp startDate) { this.startDate = startDate; }
    public Timestamp getEndDate() { return endDate; }
    public void setEndDate(Timestamp endDate) { this.endDate = endDate; }
    public List<RankEntry> getRanking() { return ranking; }
    public void setRanking(List<RankEntry> ranking) { this.ranking = ranking; }
}