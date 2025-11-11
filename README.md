# Chess
A client-server application written in Java that uses Socket programming which enables communication between two devices. It uses files for persistency.

## Main Idea and Requirements
Create a console-based chess game. The game should be designed as client-server
application. One server should be able to host many players and games. For the sake of
simplicity you can use Sockets and your own communication protocol. Use files for
persistency.
### Server functionalities:
- Manage users. User can register and then login. The server should keep
username/password, name and some statistics for each user (played games,
won games). Optionally you can think about a rating system based on game
results.
- Manage games. Users can request a game and when two users are available for
a game the server creates one and assigns them. Optionally the user matching
can be based on rating similarity.
- Record games. For each game, track the time and moves. The server should
validate each move and if not legal should return to the client meaningful
message with reason, like “Can’t move bishop to g5: illegal move”. Also the
server should detect situations like “Check” or “Checkmate” and display them
to the user.
- In case a player runs out of time - end the game and notify the clients to display
win/lose screens.
### Client functionalities:
- Register/login to the server
- View own statistics
- Review played games (list of games, date, opponent, win/loss) as well as review
the moves in the game.
- Start new game
### Additions:
- Gameplay – draw the board, accept input as any notation (for example portable
game notation or your own one), send the move to the server and redraw the
board.
- There should be an option to offer draw (could be accepted or declined by the
other user) or surrender.
- Show statistics for the game.
- In case of closing the client or network error, the user should be able to restart
the client within one minute before getting dropped from the game. In case the
client (console app) is restarted and there is an ongoing game – the user should
directly enter the game.
