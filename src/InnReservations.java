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
        roomsAndReservations(stmt);
      } catch (SQLException e) {
        System.out.println("Unable to execute query:\n" + e.getMessage());
      }
    } catch (SQLException e) {
      System.out.println("CONNECTION FAILURE");
    }
  }
  private static void roomsAndReservations(Statement stmt) throws SQLException {
    String fr1 =
      """
          select
            fr1.RoomCode, RoomName, Beds, bedType, maxOcc, basePrice, decor, latestcheckout,
            case when Popularity > 1 then 1.0 else Popularity end as Popularity, NextAvailable from
            (select RoomCode, round(
                sum(case when (
                    (datediff(curdate(), checkin) > 180) and (datediff(curdate(), checkout) < 180))
                    then datediff(checkout, DATE_ADD(curdate(), INTERVAL -180 DAY))
                    when (datediff(checkout, checkin) > 180) then 180
                    else datediff(checkout, checkin) end)/180, 2) as popularity,
                    DATE_ADD(curdate(), INTERVAL min(case when datediff(checkout, curdate()) < 0 then NULL else datediff(checkout, curdate()) end) DAY) as NextAvailable,
                    max(case when datediff(checkout, curdate()) > 0 then NULL else checkout end) as latestcheckout
            from swallan.lab7_rooms as r1
                join swallan.lab7_reservations as r2 on r1.RoomCode = r2.Room
            where datediff(curdate(), checkin) <= 180 or datediff(curdate(), checkout) < 180
            group by RoomCode) as fr1
                join swallan.lab7_rooms as r on r.RoomCode = fr1.RoomCode
            order by popularity desc
        """;
    ResultSet rs = stmt.executeQuery(fr1);
    while (rs.next()) {
      String code = rs.getString("RoomCode");
      String popularity = rs.getString("popularity");
      String name = rs.getString("RoomName");
      String beds = rs.getString("Beds");
      String bedType = rs.getString("bedType");
      String maxOcc = rs.getString("maxOcc");
      String basePrice = rs.getString("basePrice");
      String decor = rs.getString("decor");
      String latestCheckout = rs.getString("latestcheckout");
      String nextAvail = rs.getString("NextAvailable");
      System.out.format("RoomCode: %s, RoomName: %s, Beds: %s, bedType: %s, maxOcc: %s, basePrice: %s, decor: %s, " +
        "latestCheckout: %s, popularity: %s, NextAvailable: %s\n", code, name, beds, bedType, maxOcc, basePrice,
        decor, latestCheckout, popularity, nextAvail);
    }
  }
}
