package com.example.chess.common;

public class UserModels {

    public static class Stats {
        public int played;
        public int won;
        public int rating = 1500;
    }

    public static class User {
        public String username;
        public String name;
        public String passHash;  // PBKDF2 hash stored on server
        public Stats stats = new Stats();
    }
}

