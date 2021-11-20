
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import java.sql.*;


public class InnReservations {

  public static final String ROOM_CODE = "RoomCode";
  public static final String ROOM_NAME = "RoomName";
  public static final String HP_JDBC_URL = "HP_JDBC_URL";
  public static final String HP_JDBC_USER = "HP_JDBC_USER";
  public static final String HP_JDBC_PW = "HP_JDBC_PW";
  public static final String NO_CHANGE = "no change";

  private static void bookReservation(BufferedReader reader) throws SQLException, IOException {
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
          select * from swallan.lab7_rooms as rm where RoomCode not in 
          (select Room from swallan.lab7_reservations as rv where (CheckIn between '%s' and '%s' or Checkout between '%s' and '%s'))
          and maxOcc >= (%s)""",
            df.format(checkin),
            df.format(checkout),
            df.format(checkin),
            df.format(checkout),
            rDetails.nadults + rDetails.nchildren);
        try (PreparedStatement preparedStatement = conn.prepareStatement("""
          select * from swallan.lab7_rooms as rm where RoomCode not in 
          (select Room from swallan.lab7_reservations as rv where (CheckIn between ? and  ? or Checkout between ? and ?))
          and maxOcc >= (?)""")) {

          preparedStatement.setString(1,  df.format(checkin));
          preparedStatement.setString(2,  df.format(checkout));
          preparedStatement.setString(3,  df.format(checkin));
          preparedStatement.setString(4,  df.format(checkout));
          preparedStatement.setInt(5, rDetails.nadults + rDetails.nchildren);
          ResultSet rs = preparedStatement.executeQuery();
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
      String sql = "select max(maxOcc) as maxOccupancy from swallan.lab7_rooms";
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
      String sqlGetNewRNumber = "select (max(CODE) + 1) as newCode from swallan.lab7_reservations";
      try (Statement stmt = conn.createStatement();
           ResultSet rs = stmt.executeQuery(sqlGetNewRNumber)) {
        while (rs.next()) {
          newCode = rs.getLong("newCode");
        }
      }

      // figure out what the baseprice is.
      Long basePrice = null;
      String getBasePrice = "select basePrice from swallan.lab7_rooms";
      try (Statement stmt = conn.createStatement();
           ResultSet rs = stmt.executeQuery(getBasePrice)) {
        while (rs.next()) {
          basePrice = rs.getLong("basePrice");
        }
      }
      String input = getResponse(reader);
      if (input.equals("C")) {
        try (PreparedStatement stmt = conn.prepareStatement("insert into swallan.lab7_reservations (CODE, Room, CheckIn, Checkout, Rate, LastName, FirstName, Adults, Kids) VALUES  (?, ?,?,?,?, ?, ?, ?, ?)")
        ) {
          stmt.setLong(1, newCode);
          stmt.setString(2, code);
          stmt.setString(3, rDetails.checkin);
          stmt.setString(4, rDetails.checkout);
          stmt.setLong(5, basePrice);
          stmt.setString(6, rDetails.firstName);
          stmt.setString(7, rDetails.lastName);
          stmt.setLong(8, rDetails.nadults);
          stmt.setLong(9, rDetails.nchildren);

          int rs = stmt.executeUpdate();
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




      try (PreparedStatement stmt = conn.prepareStatement("""
          select * from swallan.lab7_rooms as rm where RoomCode not in 
          (select Room from swallan.lab7_reservations as rv where (CheckIn between ? and ? or Checkout between ? and ?))
          and maxOcc >= (?) and roomCode like ? and bedType like ?""");
           ) {
        stmt.setString(1, rDetails.checkin);
        stmt.setString(2, rDetails.checkin);
        stmt.setString(3, rDetails.checkin);
        stmt.setString(4, rDetails.checkout);
        stmt.setLong(5, rDetails.nadults + rDetails.nchildren);
        if (rDetails.bedType.equals("Any")) {
          stmt.setString(6, "%");
        } else {
          stmt.setString(6, rDetails.bedType);
        }

        if (rDetails.roomCode.equals("Any")) {
          stmt.setString(7, "%");
        } else {
          stmt.setString(7, rDetails.roomCode);
        }
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
          rooms.add(new Room(rs.getString(ROOM_CODE), rs.getString(ROOM_NAME)));
        }
      }
    }
    return rooms;
  }


  private static ReservationDetails takeReservationDetails(BufferedReader s) throws IOException {
//     return new ReservationDetails("firstName", "lastName", "HBB",
//         "Queen", "2021-10-20", "2021-12-25", 1, 1);
   System.out.println("Input first name");
   String firstName = s.readLine();
   System.out.println("Input last name");
   String lastName = s.readLine();
   System.out.println("Input room code if preference, else 'Any'");
   String roomCode = s.readLine();
   System.out.println("Input bed type if preference, else 'Any'");
   String bedType = s.readLine();
   System.out.println("Input checkin date (yyyy-mm-dd)");
   String checkin = s.readLine();
   System.out.println("Input checkout date (yyyy-mm-dd)");
   String checkout = s.readLine();
   System.out.println("Input count children (1, 2, ...)");
   int nchildren = Integer.parseInt(s.readLine());
   System.out.println("Input count adults (1, 2, ...)");
   int nadults = Integer.parseInt(s.readLine());
   return new ReservationDetails(firstName, lastName, roomCode, bedType, checkin, checkout, nchildren, nadults);
  }

  private static void changeReservation(Connection conn, Scanner s) throws SQLException {
    try (PreparedStatement pstmt2 = conn.prepareStatement("""
  select Room from swallan.lab7_reservations where CODE = ?
""")) {

      System.out.println("Input 'no change' for any field you do not wish to change\n");
      System.out.println("Enter reservation code");
      String resCode = s.nextLine();
      pstmt2.setString(1, resCode);
      ResultSet rs = pstmt2.executeQuery();
      rs.next();
      String room = rs.getString("Room");

      System.out.println("Enter first name");
      String fName = s.nextLine();
      if (!fName.equals(NO_CHANGE)) {
        PreparedStatement pstmt = conn.prepareStatement
          ("""
        UPDATE swallan.lab7_reservations
        SET FirstName = ?
        WHERE CODE = ?
        """);
        pstmt.setString(1, fName);
        pstmt.setInt(2, Integer.parseInt(resCode));
        pstmt.executeUpdate();
      }
      System.out.println("Enter last name");
      String lName = s.nextLine();
      if (!lName.equals(NO_CHANGE)) {
        PreparedStatement pstmt = conn.prepareStatement
          ("""
        UPDATE swallan.lab7_reservations
        SET LastName = ?
        WHERE CODE = ?
        """);
        pstmt.setString(1, lName);
        pstmt.setInt(2, Integer.parseInt(resCode));
        pstmt.executeUpdate();
      }
      System.out.println("Enter begin date (yyyy-mm-dd)");
      String checkin = s.nextLine();
      if (!checkin.equals(NO_CHANGE)) {
        if (checkDate(conn, checkin, room)) {
          PreparedStatement pstmt = conn.prepareStatement
            ("""
        UPDATE swallan.lab7_reservations
        SET CheckIn = ?
        WHERE CODE = ?
        """);
          pstmt.setDate(1, Date.valueOf(checkin));
          pstmt.setInt(2, Integer.parseInt(resCode));
          pstmt.executeUpdate();
        } else {
          System.err.println("Unable to update checkin date, it lands within another reservation.");
        }
      }
      System.out.println("Enter end date (yyyy-mm-dd)");
      String checkout = s.nextLine();
      if (!checkout.equals(NO_CHANGE)) {
        if (checkDate(conn, checkout, room)) {
          PreparedStatement pstmt = conn.prepareStatement
            ("""
        UPDATE swallan.lab7_reservations
        SET CheckOut = ?
        WHERE CODE = ?
        """);
          pstmt.setDate(1, Date.valueOf(checkout));
          pstmt.setInt(2, Integer.parseInt(resCode));
          pstmt.executeUpdate();
        }else {
          System.err.println("Unable to update checkout date, it lands within another reservation.");
        }
      }
      System.out.println("Enter number of children");
      String numChildren = s.nextLine();
      if (!numChildren.equals(NO_CHANGE)) {
        PreparedStatement pstmt = conn.prepareStatement
          ("""
        UPDATE swallan.lab7_reservations
        SET Kids = ?
        WHERE CODE = ?
        """);
        pstmt.setInt(1, Integer.parseInt(numChildren));
        pstmt.setInt(2, Integer.parseInt(resCode));
        pstmt.executeUpdate();
      }
      System.out.println("Enter number of adults");
      String numAdults = s.nextLine();
      if (!numAdults.equals(NO_CHANGE)) {
        PreparedStatement pstmt = conn.prepareStatement
          ("""
        UPDATE swallan.lab7_reservations
        SET Adults = ?
        WHERE CODE = ?
        """);
        pstmt.setInt(1, Integer.parseInt(numAdults));
        pstmt.setInt(2, Integer.parseInt(resCode));
        pstmt.executeUpdate();
      }
    }
  }

  /**
   * Checks if a date will land within an existing stays checkin and checkout range
   * @param conn
   * @param date
   * @param room
   * @return True if no problem, false if the new date will be in another range in the same room
   * @throws SQLException
   */
  private static boolean checkDate(Connection conn, String date, String room) throws SQLException {
    PreparedStatement pstmt =
      conn.prepareStatement("""
      select * from swallan.lab7_reservations where checkin <= ? and checkout > ? and room = ?
      """);
    pstmt.setDate(1, java.sql.Date.valueOf(date));
    pstmt.setDate(2, java.sql.Date.valueOf(date));
    pstmt.setString(3, room);
    ResultSet rs = pstmt.executeQuery();
    int size =0;
    if (rs != null)
    {
      boolean check = rs.last();    // moves cursor to the last row
      if (check) {
        size = rs.getRow(); // get row id
      }
    }
    if (size == 0) {
      return true;
    } else {
      return false;
    }
  }

  private static void detailedResInformation(Connection conn, Scanner scanner) throws SQLException {
    PreparedStatement pstmt = conn.prepareStatement(
"""
    select CODE, RoomName, Room, CheckIn, CheckOut, Rate, LastName, FirstName, Adults, Kids from
     swallan.lab7_reservations as r
         join swallan.lab7_rooms as r2 on Room = RoomCode
      WHERE FirstName LIKE ? and LastName LIKE ? and Room LIKE ? and code LIKE ?
      and ((? <= CheckIn and CheckIn <= ?) or (? <= CheckOut and CheckOut <= ?))
    """);
    PreparedStatement pstmtRightBoundOnly = conn.prepareStatement(
      """
          select CODE, RoomName, Room, CheckIn, CheckOut, Rate, LastName, FirstName, Adults, Kids from
           swallan.lab7_reservations as r
               join swallan.lab7_rooms as r2 on Room = RoomCode
            WHERE FirstName LIKE ? and LastName LIKE ? and Room LIKE ? and code LIKE ?
            and (CheckIn <= ? or CheckOut <= ?)
          """);
    PreparedStatement pstmtLeftBoundOnly = conn.prepareStatement(
      """
          select CODE, RoomName, Room, CheckIn, CheckOut, Rate, LastName, FirstName, Adults, Kids from
           swallan.lab7_reservations as r
               join swallan.lab7_rooms as r2 on Room = RoomCode
            WHERE FirstName LIKE ? and LastName LIKE ? and Room LIKE ? and code LIKE ?
            and (? <= CheckIn or ? <= CheckOut)
          """);
    PreparedStatement pstmtNoDates = conn.prepareStatement(
      """
          select CODE, RoomName, Room, CheckIn, CheckOut, Rate, LastName, FirstName, Adults, Kids from
           swallan.lab7_reservations as r
               join swallan.lab7_rooms as r2 on Room = RoomCode
            WHERE FirstName LIKE ? and LastName LIKE ? and Room LIKE ? and code LIKE ?
          """);
    System.out.println("Input information to query reservations. Blank entry will indicate any\n");
    System.out.println("Enter firstname");
    String fname = scanner.nextLine();
    System.out.println("Enter last name");
    String lname = scanner.nextLine();
    System.out.println("Enter a room code");
    String room = scanner.nextLine();
    System.out.println("Enter date range left bound");
    String firstDate = scanner.nextLine();
    System.out.println("Enter date range right bound");
    String lastDate = scanner.nextLine();
    System.out.println("Enter reservation code");
    String resCode = scanner.nextLine();
    if (resCode.equals("")) {
      resCode = "%";
    }
    if (fname.equals("")) {
      fname = "%";
    }
    if (lname.equals("")) {
      lname = "%";
    }
    if (room.equals("")) {
      room = "%";
    }
    ResultSet rs;
    if (firstDate.equals("") && lastDate.equals("")) {
      pstmtNoDates.setString(1, fname);
      pstmtNoDates.setString(2, lname);
      pstmtNoDates.setString(3, room);
      pstmtNoDates.setString(4, resCode);
      rs = pstmtNoDates.executeQuery();
    } else if (firstDate.equals("")) {
      pstmtRightBoundOnly.setString(1, fname);
      pstmtRightBoundOnly.setString(2, lname);
      pstmtRightBoundOnly.setString(3, room);
      pstmtRightBoundOnly.setString(4, resCode);
      pstmtRightBoundOnly.setDate(5, java.sql.Date.valueOf(lastDate));
      pstmtRightBoundOnly.setDate(6, java.sql.Date.valueOf(lastDate));
      rs = pstmtRightBoundOnly.executeQuery();
    } else if (lastDate.equals("")) {
      pstmtLeftBoundOnly.setString(1, fname);
      pstmtLeftBoundOnly.setString(2, lname);
      pstmtLeftBoundOnly.setString(3, room);
      pstmtLeftBoundOnly.setString(4, resCode);
      pstmtLeftBoundOnly.setDate(5, java.sql.Date.valueOf(firstDate));
      pstmtLeftBoundOnly.setDate(6, java.sql.Date.valueOf(firstDate));
      rs = pstmtLeftBoundOnly.executeQuery();
    } else {
      pstmt.setString(1, fname);
      pstmt.setString(2, lname);
      pstmt.setString(3, room);
      pstmt.setString(4, resCode);
      pstmt.setDate(5, java.sql.Date.valueOf(firstDate));
      pstmt.setDate(6, java.sql.Date.valueOf(lastDate));
      pstmt.setDate(7, java.sql.Date.valueOf(firstDate));
      pstmt.setDate(8, java.sql.Date.valueOf(lastDate));
      rs = pstmt.executeQuery();
    }
    while (rs.next()) {
      String formatOut =
        ("Res Code: %s, RoomName: %s, RoomCode: %s, CheckIn: %s, CheckOut: %s, Rate: %s, LastName: %s, FirstName: %s, " +
          "Adults: %s, Kids: %s").formatted(rs.getString("CODE"), rs.getString("RoomName"), rs.getString("Room"),
          rs.getString("CheckIn"), rs.getString("CheckOut"), rs.getString("Rate"), rs.getString("LastName"),
          rs.getString("FirstName"), rs.getString("Adults"), rs.getString("Kids"));
      System.out.println(formatOut);
    }
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

        List<String> createMonthsTables = List.of("drop table if exists swallan.months;",
            """
            CREATE TABLE IF NOT EXISTS swallan.months
            (
                m int PRIMARY KEY
            );""",
            "insert into months VALUE (1);",
            "insert into months VALUE (2);",
            "insert into months VALUE (3);",
            "insert into months VALUE (4);",
            "insert into months VALUE (5);",
            "insert into months VALUE (6);",
            "insert into months VALUE (7);",
            "insert into months VALUE (8);",
            "insert into months VALUE (9);",
            "insert into months VALUE (10);",
            "insert into months VALUE (11);",
            "insert into months VALUE (12);");
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
        for (String s : createMonthsTables) {
          stmt.addBatch(s);
        }
        stmt.executeBatch();
      }

      try (Statement stmt = conn.createStatement()) {
        stmt.execute(createRooms);
        stmt.execute(createReservations);
//        stmt.executeQuery(createMonthsTable);

        try (var reader = new BufferedReader(new InputStreamReader(System.in))) {
          System.out.println("CSC 365 Lab 7\nBy Fin and Sam\n");

          String menu = """
            Enter a number
            1: Rooms and Reservations
            2: Reservations
            3: Reservation Change
            4: Reservation Cancellation
            5: Detailed Reservation Information
            6: Revenue
            0: Exit
            """;
          System.out.println(menu);
          String response = getResponse(reader);
          while (response.charAt(0) != '0') {

            switch (response.charAt(0)) {
              case '1':
                roomsAndReservations(stmt);
                break;
              case '2':
                bookReservation(reader);
                break;
              case '3':
                changeReservation(conn,new Scanner(System.in));
                break;
              case '4':
                deleteReservation(reader);
                break;
              case '5':
                detailedResInformation(conn, new Scanner(System.in));
                break;
              case '6':
                showRevenue();
                break;
            }
            System.out.println(menu);
            response = getResponse(reader);
          }
        } catch (IOException e) {
          System.out.println("Error initiating reader.");
        }
      } catch (SQLException e) {
        System.out.println("Unable to execute query:\n" + e.getLocalizedMessage());
        e.printStackTrace();
      }
    } catch (SQLException e) {
      System.out.println("CONNECTION FAILURE");
    }

  }

  private static void showRevenue() {

    String sql = """
        with funStuff as (select *,
               IF(MONTH(CheckIn) = MONTH(Checkout),
                    DATEDIFF(checkout, checkin), -- just the number of days in the reservation
                    if (MONTH(CheckOut) <> m and MONTH(Checkin) <> m,
                        DAY(LAST_DAY(DATE_ADD(MAKEDATE(2008, 7), INTERVAL (m - 1) MONTH))), -- full month
                        -- partial month
                        IF(MONTH(checkout) = m,
                           DAY(checkout),
                           DAY(LAST_DAY(DATE_ADD(MAKEDATE(2008, 7), INTERVAL (m - 1) MONTH))) +1 - DAY(checkin)
                          )
                       )
                  ) as daysStayed
                
        from swallan.lab7_reservations join months on (MONTH(CheckIn) <= months.m and MONTH(Checkout) >= months.m)
        where MONTH(Checkout) <> MONTH(Checkin)
        order by CODE DESC)
                
        select m, sum(daysStayed * rate) as money, room from funStuff
        group by m, room
        order by m, room""";

    Map<String, List<Double>> roomsAndRevenues = new HashMap<>();
    try (Connection conn = DriverManager.getConnection(System.getenv(HP_JDBC_URL),
        System.getenv(HP_JDBC_USER),
        System.getenv(HP_JDBC_PW))) {
      boolean isR = false;
      try (Statement stmt = conn.createStatement()) {
        ResultSet rs = stmt.executeQuery(sql);
        while (rs.next()) {
          if (roomsAndRevenues.containsKey(rs.getString("room"))) {
            roomsAndRevenues.get(rs.getString("room")).add(Double.parseDouble(rs.getString("money")));
          } else {
            List<Double> temp = new ArrayList<>();
            temp.add(Double.parseDouble(rs.getString("money")));

            roomsAndRevenues.put(rs.getString("room"), temp);
          }
        }
      }
      System.out.println("Showing revenue for each room, by month.");
      for (Map.Entry<String, List<Double>> e : roomsAndRevenues.entrySet()) {
        System.out.println(String.format("Revenue for room %s", e.getKey()));
        for (int i = 1; i < 13; i++) {
          double rev = 0;
          if (e.getValue().size() > i) {
            rev = e.getValue().get(i);
          }
          System.out.println(String.format("Month %s: %s", i, Math.round(rev)));

        }
        System.out.println(String.format("Total revenue: %s\n", Math.round(e.getValue().stream().mapToDouble(f -> f.doubleValue()).sum())));
      }
    } catch (SQLException throwables) {
      throwables.printStackTrace();
    }
  }

    private static void deleteReservation(BufferedReader reader) throws SQLException {
    System.out.println("Please enter your confirmation code for cancellation.");
    String code = getResponse(reader);
    System.out.println(String.format("Please confirm [C] cancellation for <%s>.\nPress any other key to keep your reservation.", code));
    if (getResponse(reader).equals("C")){
      // cancel reservation
      String sql = String.format("delete from swallan.lab7_reservations where CODE = %s", code);
      String sql2 = String.format("select * from swallan.lab7_reservations where CODE = %s", code);
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
