import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

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
    } catch (SQLException e) {
      System.out.println("CONNECTION FAILURE");
    }
  }
}
