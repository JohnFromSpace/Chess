package com.example.chess.common;

public class UserModels {

    public static class Stats {
        private int played;
        private int won;
        private int drawn;
        private int rating;
        private int lost;

        public Stats() {
            setPlayed(0);
            setWon(0);
            setDrawn(0);
            setLost(0);
            setRating(1200);
        }

        public void setPlayed(int played) {this.played = played;}
        public void setWon(int won) {this.won = won;}
        public void setDrawn(int drawn) {this.drawn = drawn;}
        public void setRating(int rating) {this.rating = rating;}
        public void setLost(int lost) {this.lost = lost;}

        public int getPlayed() {return played;}
        public int getWon() {return won;}
        public int getDrawn() {return drawn;}
        public int getRating() {return rating;}
        public int getLost() {return lost;}
    }

    public static class User {
        private String username;
        private String passHash;  // PBKDF2 hash stored on server
        public Stats stats = new Stats();

        public void setUsername(String username) {this.username = username;}
        public void setPassHash(String passHash) {this.passHash = passHash;}

        public String getUsername() {return username;}
        public String getPassHash() {return passHash;}
    }
}

