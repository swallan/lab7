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

public class InnReservations {

  public static final String ROOM_CODE = "RoomCode";
  public static final String ROOM_NAME = "RoomName";
  public static final String HP_JDBC_URL = "HP_JDBC_URL";
  public static final String HP_JDBC_USER = "HP_JDBC_USER";
  public static final String HP_JDBC_PW = "HP_JDBC_PW";

  private static void bookReservation() throws SQLException {
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
    try (var s = new Scanner(System.in)) {
      ReservationDetails rDetails = takeReservationDetails(s);
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
          String code = s.nextLine();
          if(!code.equals("CANCEL")) {
            makeProspectiveBooking(rDetails, code, s);
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
        String code = s.nextLine();
        if(!code.equals("CANCEL")) {
          makeProspectiveBooking(rDetails, code, s);
        }
      }
    }
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


//                Room.builder().roomCode(rs.getString("roomCode"))
//            .roomName(rs.getString("roomName")))//new Room(rs.getString("roomCode"), rs.getString("roomName"), ));
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

  private static void makeProspectiveBooking(ReservationDetails rDetails, String code, Scanner s) throws SQLException {
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
      String input = s.nextLine();
      if (input.equals("C")) {
        String sql = String.format("insert into lab7_reservations (CODE, Room, CheckIn, Checkout, Rate, LastName, FirstName, Adults, Kids) VALUES  (%s, '%s','%s','%s', %s, '%s', '%s', %s, %s)", newCode, code, rDetails.checkin, rDetails.checkout, basePrice, rDetails.firstName, rDetails.lastName, rDetails.nadults, rDetails.nchildren);
        try (Statement stmt = conn.createStatement()
        ) {
          int rs = stmt.executeUpdate(sql);
        }
        System.out.println("Thank you for booking.");
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


  private static ReservationDetails takeReservationDetails(Scanner s) {
    return new ReservationDetails("firstName", "lastName", "Any",
        "Any", "2021-10-20", "2021-10-25", 1, 1);
//    System.out.println("Input first name");
//    String firstName = s.nextLine();
//    System.out.println("Input last name");
//    String lastName = s.nextLine();
//    System.out.println("Input room code if preference, else 'Any'");
//    String roomCode = s.nextLine();
//    System.out.println("Input bed type if preference, else 'Any'");
//    String bedType = s.nextLine();
//    System.out.println("Input checkin date (mm-dd-yyyy)");
//    String checkin = s.nextLine();
//    System.out.println("Input checkout date (mm-dd-yyyy)");
//    String checkout = s.nextLine();
//    System.out.println("Input count children (1, 2, ...)");
//    int nchildren = Integer.parseInt(s.nextLine());
//    System.out.println("Input count adults (1, 2, ...)");
//    int nadults = Integer.parseInt(s.nextLine());
//    return new ReservationDetails(firstName, lastName, roomCode, bedType, checkin, checkout, nchildren, nadults);
  }


  public static void main(String[] args) throws SQLException {
    try{
      Class.forName("com.mysql.cj.jdbc.Driver");
      System.out.println("MySQL JDBC Driver loaded");
    } catch (ClassNotFoundException ex) {
      System.err.println("Unable to load JDBC Driver");
      System.exit(-1);
    }
    bookReservation();
//    try (Connection conn = DriverManager.getConnection(System.getenv("HP_JDBC_URL"),
//          System.getenv("HP_JDBC_USER"),
//          System.getenv("HP_JDBC_PW"))) {
//      System.out.println("CONNECTION SUCCESS");
//
//      // Step 2: Construct SQL statement
//      String sql = "SELECT * FROM lab7_rooms";
//
//      // Step 3: (omitted in this example) Start transaction
//
//      // Step 4: Send SQL statement to DBMS
//      try (Statement stmt = conn.createStatement();
//           ResultSet rs = stmt.executeQuery(sql)) {
//        // Step 5: Receive results
//        while (rs.next()) {
//          String roomCode = rs.getString("RoomCode");
//          String roomName = rs.getString("RoomName");
//          System.out.format("Roomcode: %s Roomname: %s\n", roomCode, roomName);
//        }
//      }
//    } catch (SQLException e) {
//      System.out.println("CONNECTION FAILURE");
//    }
  }
}
