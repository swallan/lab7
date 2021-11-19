
public class Room {
  String roomCode;
  String roomName;
  int beds;
  String bedType;
  int maxOcc;
  float basePrice;

  public Room(String roomCode, String roomName, int beds,
              String bedType, int maxOcc, float basePrice,
              String decor) {
    this.roomCode = roomCode;
    this.roomName = roomName;
    this.beds = beds;
    this.bedType = bedType;
    this.maxOcc = maxOcc;
    this.basePrice = basePrice;
    this.decor = decor;
  }

  String decor;

  public Room(String roomCode, String roomName) {
    this.roomCode = roomCode;
    this.roomName = roomName;
    this.beds = -1;
    this.maxOcc = -1;
  }
}
