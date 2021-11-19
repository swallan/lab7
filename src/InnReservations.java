
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import java.sql.*;


public class InnReservations {

  public static final String ROOM_CODE = "RoomCode";
  public static final String ROOM_NAME = "RoomName";
  public static final String HP_JDBC_URL = "HP_JDBC_URL";
  public static final String HP_JDBC_USER = "HP_JDBC_USER";
  public static final String HP_JDBC_PW = "HP_JDBC_PW";

  private static void bookReservation(BufferedReader reader) throws SQLException {
    /*
    When this option is selected, your system shall accept from the user the
    following information:
    • First name
    • Last name
    • A room code to indicate the specific room desired (or “Any” to indicate no preference)
    • A desired bed type (or “Any” to indicate no preference)
    • Begin date of stay
    • End date of stay
    • Number of children
    • Number of adults
    */
//    try  {
      ReservationDetails rDetails = takeReservationDetails(reader);
      List<Room> rooms = getPossibleRooms(rDetails);

      if (rooms.isEmpty()) {
        // issue bookingi room.
        if (exceedsOccupancy(rDetails.nchildren + rDetails.nadults)){
          System.out.println("Sorry, no rooms available.");
        } else {
          List<String> others = findAvailableReservation(rDetails);
          System.out.println("Sorry, no rooms were available for that date. How about these?");
          for (String r:others){
            System.out.println(r);
          }
          System.out.println("Input room code to book one, or type `CANCEL` to cancel.");
          String code = getResponse(reader);
          if(!code.equals("CANCEL")) {
            makeProspectiveBooking(rDetails, code, reader);
          }
          // TODO:
          // - need to find similar possible bookings.
        }
      } else {
        // print out exact matches and allow booking.
        System.out.println("The Following Rooms Satisfy your Requirements:\nRoomCode, Name\n----------------");
        for (Room r:rooms){
          System.out.println(String.format("%s | %s", r.roomCode, r.roomName));
        }
        System.out.println("Input room code to book one, or type `CANCEL` to cancel.");
        String code = getResponse(reader);
        if(!code.equals("CANCEL")) {
          makeProspectiveBooking(rDetails, code, reader);
        }
      }
//    }
  }


  private static List<String> findAvailableReservation(ReservationDetails rDetails) throws SQLException {
    List<String> rooms = new ArrayList<>();

    LocalDate checkin = LocalDate.parse(rDetails.checkin);
    LocalDate checkout = LocalDate.parse(rDetails.checkout);
    try (Connection conn = DriverManager.getConnection(System.getenv(HP_JDBC_URL),
        System.getenv(HP_JDBC_USER),
        System.getenv(HP_JDBC_PW))) {
      DateTimeFormatter df = DateTimeFormatter.ofPattern("MM-dd-yyy");
      while (rooms.size() < 5) {
        checkin = checkin.plusDays(1);
        checkout = checkout.plusDays(1);
        String sql = String.format("""
          select * from lab7_rooms as rm where RoomCode not in 
          (select Room from lab7_reservations as rv where (CheckIn between '%s' and '%s' or Checkout between '%s' and '%s'))
          and maxOcc >= (%s)""",
            df.format(checkin),
            df.format(checkout),
            df.format(checkin),
            df.format(checkout),
            rDetails.nadults + rDetails.nchildren);
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
          while (rs.next() && rooms.size() < 5) {
            rooms.add(String.format("%s | %s from %s to %s",rs.getString("roomCode"), rs.getString("roomName"), df.format(checkin), df.format(checkout)));
          }
        }
      }

    }
    return rooms;
  }

  /** Check if <i> people can fit into any room.
   * @param i
   * @return
   * @throws SQLException
   */
  private static boolean exceedsOccupancy(int i) throws SQLException {
    try (Connection conn = DriverManager.getConnection(System.getenv(HP_JDBC_URL),
        System.getenv(HP_JDBC_USER),
        System.getenv(HP_JDBC_PW))) {
      String sql = "select max(maxOcc) as maxOccupancy from lab7_rooms";
      try (Statement stmt = conn.createStatement();
           ResultSet rs = stmt.executeQuery(sql)) {
        while (rs.next()) {
          return i >= rs.getLong("maxOccupancy");
        }
      }
    }
    return true;
  }

  private static void makeProspectiveBooking(ReservationDetails rDetails, String code, BufferedReader reader) throws SQLException {
    try (Connection conn = DriverManager.getConnection(System.getenv(HP_JDBC_URL),
        System.getenv(HP_JDBC_USER),
        System.getenv(HP_JDBC_PW))) {

      // show what booking would be
      System.out.print(String.format("Booking details: \n%s\n [C] - Confirm\n [N] - Cancel\n", rDetails.getReservationDetails(code)));

      // collect new code for reservation.
      Long newCode = null;
      String sqlGetNewRNumber = "select (max(CODE) + 1) as newCode from lab7_reservations";
      try (Statement stmt = conn.createStatement();
           ResultSet rs = stmt.executeQuery(sqlGetNewRNumber)) {
        while (rs.next()) {
          newCode = rs.getLong("newCode");
        }
      }

      // figure out what the baseprice is.
      Long basePrice = null;
      String getBasePrice = "select basePrice from lab7_rooms";
      try (Statement stmt = conn.createStatement();
           ResultSet rs = stmt.executeQuery(getBasePrice)) {
        while (rs.next()) {
          basePrice = rs.getLong("basePrice");
        }
      }
      String input = getResponse(reader);
      if (input.equals("C")) {
        String sql = String.format("insert into lab7_reservations (CODE, Room, CheckIn, Checkout, Rate, LastName, FirstName, Adults, Kids) VALUES  (%s, '%s','%s','%s', %s, '%s', '%s', %s, %s)", newCode, code, rDetails.checkin, rDetails.checkout, basePrice, rDetails.firstName, rDetails.lastName, rDetails.nadults, rDetails.nchildren);
        try (Statement stmt = conn.createStatement()
        ) {
          int rs = stmt.executeUpdate(sql);
        }
        System.out.println("Thank you for booking.");
        System.out.print(String.format("Your reservation code is: <%s>", newCode));
      } else{
        System.out.println("Booking aborted.");
      }



    }
  }

  private static List<Room> getPossibleRooms(ReservationDetails rDetails) throws SQLException {
    List<Room> rooms = new ArrayList<>();
    try (Connection conn = DriverManager.getConnection(System.getenv(HP_JDBC_URL),
        System.getenv(HP_JDBC_USER),
        System.getenv(HP_JDBC_PW))) {

      String bedTypeSql;
      if (rDetails.bedType.equals("Any")) {
        bedTypeSql = "";
      } else {
        bedTypeSql = String.format("and bedType = %s", rDetails.bedType);
      }

      String roomCode;
      if (rDetails.roomCode.equals("Any")) {
        roomCode = "";
      } else {
        roomCode =  String.format("and roomCode = %s", rDetails.roomCode);
      }

      String baseQuery = String.format("""
          select * from lab7_rooms as rm where RoomCode not in 
          (select Room from lab7_reservations as rv where (CheckIn between '%s' and '%s' or Checkout between '%s' and '%s'))
          and maxOcc >= (%s) %s %s""",
          rDetails.checkin,
          rDetails.checkout,
          rDetails.checkin,
          rDetails.checkout,
          rDetails.nadults + rDetails.nchildren,
          roomCode,
          bedTypeSql);

      try (Statement stmt = conn.createStatement();
           ResultSet rs = stmt.executeQuery(baseQuery)) {
        while (rs.next()) {
          rooms.add(new Room(rs.getString(ROOM_CODE), rs.getString(ROOM_NAME)));
        }
      }
    }
    return rooms;
  }


  private static ReservationDetails takeReservationDetails(BufferedReader s) {
     return new ReservationDetails("firstName", "lastName", "Any",
         "Any", "2021-10-20", "2021-10-25", 1, 1);
//   System.out.println("Input first name");
//   String firstName = s.nextLine();
//   System.out.println("Input last name");
//   String lastName = s.nextLine();
//   System.out.println("Input room code if preference, else 'Any'");
//   String roomCode = s.nextLine();
//   System.out.println("Input bed type if preference, else 'Any'");
//   String bedType = s.nextLine();
//   System.out.println("Input checkin date (mm-dd-yyyy)");
//   String checkin = s.nextLine();
//   System.out.println("Input checkout date (mm-dd-yyyy)");
//   String checkout = s.nextLine();
//   System.out.println("Input count children (1, 2, ...)");
//   int nchildren = Integer.parseInt(s.nextLine());
//   System.out.println("Input count adults (1, 2, ...)");
//   int nadults = Integer.parseInt(s.nextLine());
//   return new ReservationDetails(firstName, lastName, roomCode, bedType, checkin, checkout, nchildren, nadults);
  }


  public static void main(String[] args) throws SQLException {
    try{
      Class.forName("com.mysql.cj.jdbc.Driver");
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

        try (var reader = new BufferedReader(new InputStreamReader(System.in))) {
          System.out.println("CSC 365 Lab 7\nBy Fin and Sam\n");

          System.out.println("""
            Enter a number
            1: Rooms and Reservations
            2: Reservations
            3: Reservation Change
            4: Reservation Cancellation
            5: Detailed Reservation Information
            6: Revenue
            0: Exit
            """);
          String response = getResponse(reader);
          while (response.charAt(0) != '0') {

            switch (response.charAt(0)) {
              case '1':
                roomsAndReservations(stmt);
                break;
              case '2':
                // This Will start process to book reservation.
                bookReservation(reader);
                break;
              case '3':
                break;
              case '4':
                deleteReservation(reader);
                break;
              case '5':
                break;
              case '6':
                break;
            }
            System.out.println("Main Menu.");
            response = getResponse(reader);
          }
        } catch (IOException e) {
          System.out.println("Error initiating reader.");
        }
      } catch (SQLException e) {
        System.out.println("Unable to execute query:\n" + e.getMessage());
      }
    } catch (SQLException e) {
      System.out.println("CONNECTION FAILURE");
    }

  }

  private static void deleteReservation(BufferedReader reader) throws SQLException {
    System.out.println("Please enter your confirmation code for cancellation.");
    String code = getResponse(reader);
    System.out.println(String.format("Please confirm [C] cancellation for <%s>.\nPress any other key to keep your reservation.", code));
    if (getResponse(reader).equals("C")){
      // cancel reservation
      String sql = String.format("delete from lab7_reservations where CODE = %s", code);
      String sql2 = String.format("select * from lab7_reservations where CODE = %s", code);
      try (Connection conn = DriverManager.getConnection(System.getenv(HP_JDBC_URL),
          System.getenv(HP_JDBC_USER),
          System.getenv(HP_JDBC_PW))) {
        boolean isR = false;
        try (Statement stmt = conn.createStatement()) {
          ResultSet rs = stmt.executeQuery(sql2);
          while(rs.next()) {
            isR = true;
          }
        }
        if (!isR) {
          System.out.println("Invalid reservation code. Returning to main menu.");
          return;
        }

        try (Statement stmt = conn.createStatement()) {
          int rs = stmt.executeUpdate(sql);
        }
        System.out.println("cancellation successful.");
      }
    } else {
      System.out.println("Reservation kept.");
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
  private static String getResponse(final BufferedReader reader) {
    try {
      return reader.readLine();
    } catch (IOException e) {
      e.printStackTrace();
      return e.getLocalizedMessage();
    }
  }
}
