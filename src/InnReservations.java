import java.sql.*;

/*
export CLASSPATH=$CLASSPATH:mysql-connector-java-8.0.16.jar:.
export HP_JDBC_URL=jdbc:mysql://db.labthreesixfive.com/fpiroth?autoReconnect=true\&useSSL=false
export HP_JDBC_USER=fpiroth
export HP_JDBC_PW=csc365-F2021_015699797
 */
public class InnReservations {
  public static void main(String[] args) {
    try{
      Class.forName("com.mysql.jdbc.Driver");
      System.out.println("MySQL JDBC Driver loaded");
    } catch (ClassNotFoundException ex) {
      System.err.println("Unable to load JDBC Driver");
      System.exit(-1);
    }
    try (Connection conn = DriverManager.getConnection(System.getenv("HP_JDBC_URL"),
      System.getenv("HP_JDBC_USER"),
      System.getenv("HP_JDBC_PW"))) {
      System.out.println("CONNECTION SUCCESS");

        String createRooms =
          """
              CREATE TABLE IF NOT EXISTS swallan.lab7_rooms (
                RoomCode char(5) PRIMARY KEY,
                RoomName varchar(30) NOT NULL,
                Beds int(11) NOT NULL,
                bedType varchar(8) NOT NULL,
                maxOcc int(11) NOT NULL,
                basePrice DECIMAL(6,2) NOT NULL,
                decor varchar(20) NOT NULL,
                UNIQUE (RoomName)
              );
            """;
        String createReservations =
          """
          CREATE TABLE IF NOT EXISTS swallan.lab7_reservations (
            CODE int(11) PRIMARY KEY,
            Room char(5) NOT NULL,
            CheckIn date NOT NULL,
            Checkout date NOT NULL,
            Rate DECIMAL(6,2) NOT NULL,
            LastName varchar(15) NOT NULL,
            FirstName varchar(15) NOT NULL,
            Adults int(11) NOT NULL,
            Kids int(11) NOT NULL,
            FOREIGN KEY (Room) REFERENCES swallan.lab7_rooms (RoomCode)
            );
          """;
        // It errors cuz of duplicate keys so only need this if we need to reset the tables
//        String insertRooms = "INSERT INTO swallan.lab7_rooms SELECT * FROM INN.rooms;";
//        String insertReservations =
//          """
//          INSERT INTO swallan.lab7_reservations SELECT CODE, Room,
//          DATE_ADD(CheckIn, INTERVAL 132 MONTH),
//          DATE_ADD(Checkout, INTERVAL 132 MONTH),
//          Rate, LastName, FirstName, Adults, Kids FROM INN.reservations;
//          """;

      try (Statement stmt = conn.createStatement()) {
        stmt.execute(createRooms);
        stmt.execute(createReservations);


      } catch (SQLException e) {
        System.out.println("Unable to execute query:\n" + e.getMessage());
      }
    } catch (SQLException e) {
      System.out.println("CONNECTION FAILURE");
    }
  }
}
