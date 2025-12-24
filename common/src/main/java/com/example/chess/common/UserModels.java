package com.example.chess.common;

public class UserModels {

    public static class Stats {
        public int played;
        public int won;
        public int drawn;
        public int rating;
        public int lost;

        public Stats() {
            this.played = 0;
            this.won = 0;
            this.drawn = 0;
            this.lost = 0;
            this.rating = 1200;
        }
    }

    public static class User {
        public String username;
        public String name;
        public String passHash;  // PBKDF2 hash stored on server
        public Stats stats = new Stats();
    }
}

