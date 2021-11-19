
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public class ReservationDetails {
  String firstName;
  String lastName;
  String roomCode;
  String bedType;
  String checkin;
  String checkout;
  int nchildren;
  int nadults;

  public ReservationDetails(String firstName, String lastName, String roomCode,
                            String bedType, String checkin, String checkout,
                            int nchildren, int nadults) {
    this.firstName = firstName;
    this.lastName = lastName;
    this.roomCode = roomCode;
    this.bedType = bedType;
    this.checkin = checkin;
    this.checkout = checkout;
    this.nchildren = nchildren;
    this.nadults = nadults;
  }


  public String getReservationDetails(String roomCode) throws SQLException {
    // calcualte cost of stay:
    // get base price:
    Long basePrice = null;
    String roomName = null;
    String bedType = null;
    try (Connection conn = DriverManager.getConnection(System.getenv("HP_JDBC_URL"),
        System.getenv("HP_JDBC_USER"),
        System.getenv("HP_JDBC_PW"))) {

      String sqlGetNewRNumber = String.format("select * from lab7_rooms where roomcode = '%s'", roomCode);
      try (Statement stmt = conn.createStatement();
           ResultSet rs = stmt.executeQuery(sqlGetNewRNumber)) {
        while (rs.next()) {
          basePrice = rs.getLong("basePrice");
          roomName = rs.getString("roomName");
          bedType = rs.getString("bedType");
        }
      }
    }
    long weekdays = calcWeekDays1(LocalDate.parse(checkin), LocalDate.parse(checkout));
    long weekends = ChronoUnit.DAYS.between(LocalDate.parse(checkin),LocalDate.parse(checkout)) - weekdays;
    double cost = basePrice * weekdays + (1.1 * basePrice) * weekends;
    return String.format("""
        Name: %s %s
        %s %s %s
        checkin %s
        checkout %s
        %s adults
        %s children
        cost: %s
        """, firstName, lastName, roomCode, roomName, bedType, checkin, checkout, nadults, nchildren, cost);
  }


  public static long calcWeekDays1(final LocalDate start, final LocalDate end) {
    final DayOfWeek startW = start.getDayOfWeek();
    final DayOfWeek endW = end.getDayOfWeek();

    final long days = ChronoUnit.DAYS.between(start, end);
    final long daysWithoutWeekends = days - 2 * ((days + startW.getValue())/7);

    //adjust for starting and ending on a Sunday:
    return daysWithoutWeekends + (startW == DayOfWeek.SUNDAY ? 1 : 0) + (endW == DayOfWeek.SUNDAY ? 1 : 0);
  }

}

