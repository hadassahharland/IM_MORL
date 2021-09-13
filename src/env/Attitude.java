package env;

public final class Attitude {

    private static int attitude = 0;
//    private static int justification = -1;

    public static int getAttitude() {
        return attitude;
    }

    public static void setAttitude(int attitude) {
        Attitude.attitude = attitude;
    }

//    public static int getJustification() {
//        return justification;
//    }
//
//    public static void setJustification(int justification) {
//        Attitude.justification = justification;
//    }
}
