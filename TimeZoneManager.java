import java.util.TimeZone;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

public class TimeZoneManager {
    private static final Map<String, String> TIMEZONE_MAP = new HashMap<>();
    
    static {
        // 初始化时区映射
        TIMEZONE_MAP.put("Asia/Shanghai", "China Standard Time");
        TIMEZONE_MAP.put("Asia/Tokyo", "Tokyo Standard Time");
        TIMEZONE_MAP.put("Asia/Hong_Kong", "China Standard Time");
        TIMEZONE_MAP.put("Asia/Taipei", "Taipei Standard Time");
        TIMEZONE_MAP.put("Asia/Seoul", "Korea Standard Time");
        TIMEZONE_MAP.put("Asia/Singapore", "Singapore Standard Time");
        TIMEZONE_MAP.put("Asia/Kolkata", "India Standard Time");
        TIMEZONE_MAP.put("Asia/Bangkok", "SE Asia Standard Time");
        TIMEZONE_MAP.put("Asia/Jakarta", "SE Asia Standard Time");
        TIMEZONE_MAP.put("Asia/Kuala_Lumpur", "Singapore Standard Time");
        TIMEZONE_MAP.put("Asia/Manila", "Singapore Standard Time");
        TIMEZONE_MAP.put("Asia/Dubai", "Arabian Standard Time");
        TIMEZONE_MAP.put("Asia/Riyadh", "Arab Standard Time");
        TIMEZONE_MAP.put("Asia/Tehran", "Iran Standard Time");
        TIMEZONE_MAP.put("Asia/Karachi", "Pakistan Standard Time");
        TIMEZONE_MAP.put("Asia/Dhaka", "Bangladesh Standard Time");
        TIMEZONE_MAP.put("Asia/Yangon", "Myanmar Standard Time");
        TIMEZONE_MAP.put("Asia/Ho_Chi_Minh", "SE Asia Standard Time");
        
        // 美洲时区
        TIMEZONE_MAP.put("America/New_York", "Eastern Standard Time");
        TIMEZONE_MAP.put("America/Chicago", "Central Standard Time");
        TIMEZONE_MAP.put("America/Denver", "Mountain Standard Time");
        TIMEZONE_MAP.put("America/Los_Angeles", "Pacific Standard Time");
        TIMEZONE_MAP.put("America/Anchorage", "Alaskan Standard Time");
        TIMEZONE_MAP.put("America/Adak", "Aleutian Standard Time");
        TIMEZONE_MAP.put("America/Phoenix", "US Mountain Standard Time");
        TIMEZONE_MAP.put("America/Halifax", "Atlantic Standard Time");
        TIMEZONE_MAP.put("America/St_Johns", "Newfoundland Standard Time");
        TIMEZONE_MAP.put("America/Mexico_City", "Central Standard Time (Mexico)");
        TIMEZONE_MAP.put("America/Bogota", "SA Pacific Standard Time");
        TIMEZONE_MAP.put("America/Lima", "SA Pacific Standard Time");
        TIMEZONE_MAP.put("America/Santiago", "Pacific SA Standard Time");
        TIMEZONE_MAP.put("America/Caracas", "Venezuela Standard Time");
        TIMEZONE_MAP.put("America/La_Paz", "SA Western Standard Time");
        TIMEZONE_MAP.put("America/Argentina/Buenos_Aires", "Argentina Standard Time");
        TIMEZONE_MAP.put("America/Sao_Paulo", "E. South America Standard Time");
        
        // 欧洲时区
        TIMEZONE_MAP.put("Europe/London", "GMT Standard Time");
        TIMEZONE_MAP.put("Europe/Paris", "Romance Standard Time");
        TIMEZONE_MAP.put("Europe/Berlin", "W. Europe Standard Time");
        TIMEZONE_MAP.put("Europe/Rome", "W. Europe Standard Time");
        TIMEZONE_MAP.put("Europe/Madrid", "Romance Standard Time");
        TIMEZONE_MAP.put("Europe/Amsterdam", "W. Europe Standard Time");
        TIMEZONE_MAP.put("Europe/Brussels", "Romance Standard Time");
        TIMEZONE_MAP.put("Europe/Vienna", "W. Europe Standard Time");
        TIMEZONE_MAP.put("Europe/Zurich", "W. Europe Standard Time");
        TIMEZONE_MAP.put("Europe/Stockholm", "W. Europe Standard Time");
        TIMEZONE_MAP.put("Europe/Oslo", "W. Europe Standard Time");
        TIMEZONE_MAP.put("Europe/Copenhagen", "Romance Standard Time");
        TIMEZONE_MAP.put("Europe/Helsinki", "FLE Standard Time");
        TIMEZONE_MAP.put("Europe/Prague", "Central Europe Standard Time");
        TIMEZONE_MAP.put("Europe/Warsaw", "Central European Standard Time");
        TIMEZONE_MAP.put("Europe/Budapest", "Central Europe Standard Time");
        TIMEZONE_MAP.put("Europe/Bucharest", "GTB Standard Time");
        TIMEZONE_MAP.put("Europe/Moscow", "Russian Standard Time");
        TIMEZONE_MAP.put("Europe/Kiev", "FLE Standard Time");
        
        // 大洋洲时区
        TIMEZONE_MAP.put("Australia/Sydney", "AUS Eastern Standard Time");
        TIMEZONE_MAP.put("Australia/Melbourne", "AUS Eastern Standard Time");
        TIMEZONE_MAP.put("Australia/Brisbane", "E. Australia Standard Time");
        TIMEZONE_MAP.put("Australia/Adelaide", "Cen. Australia Standard Time");
        TIMEZONE_MAP.put("Australia/Perth", "W. Australia Standard Time");
        TIMEZONE_MAP.put("Australia/Darwin", "AUS Central Standard Time");
        TIMEZONE_MAP.put("Pacific/Auckland", "New Zealand Standard Time");
        TIMEZONE_MAP.put("Pacific/Fiji", "Fiji Standard Time");
        TIMEZONE_MAP.put("Pacific/Guam", "West Pacific Standard Time");
        TIMEZONE_MAP.put("Pacific/Honolulu", "Hawaiian Standard Time");
        
        // 非洲时区
        TIMEZONE_MAP.put("Africa/Cairo", "Egypt Standard Time");
        TIMEZONE_MAP.put("Africa/Johannesburg", "South Africa Standard Time");
        TIMEZONE_MAP.put("Africa/Lagos", "W. Central Africa Standard Time");
        TIMEZONE_MAP.put("Africa/Nairobi", "E. Africa Standard Time");
        TIMEZONE_MAP.put("Africa/Casablanca", "Morocco Standard Time");
    }
    
    /**
     * 获取所有可用的时区ID列表
     */
    public static List<String> getAllTimeZoneIds() {
        return Arrays.stream(TimeZone.getAvailableIDs())
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * 获取当前系统时区
     */
    public static String getCurrentTimeZone() {
        try {
            Process process = Runtime.getRuntime().exec("tzutil /g");
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()));
            return reader.readLine();
        } catch (Exception e) {
            System.err.println("Error getting current timezone: " + e.getMessage());
            return TimeZone.getDefault().getID();
        }
    }

    /**
     * 设置系统时区
     * @param timeZoneId 时区ID，例如："Asia/Shanghai"
     */
    public static void setTimeZone(String timeZoneId) {
        try {
            // 获取Windows时区ID
            String windowsTimeZoneId = TIMEZONE_MAP.get(timeZoneId);
            if (windowsTimeZoneId != null) {
                // 使用tzutil命令设置时区
                Process process = Runtime.getRuntime().exec("tzutil /s \"" + windowsTimeZoneId + "\"");
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    System.out.println("Successfully changed timezone to: " + timeZoneId);
                } else {
                    System.err.println("Failed to change timezone. Exit code: " + exitCode);
                }
            } else {
                System.err.println("Unsupported timezone: " + timeZoneId);
            }
        } catch (Exception e) {
            System.err.println("Error changing timezone: " + e.getMessage());
        }
    }

    /**
     * 获取时区的显示名称
     */
    public static String getTimeZoneDisplayName(String timeZoneId) {
        TimeZone timeZone = TimeZone.getTimeZone(timeZoneId);
        return timeZone.getDisplayName();
    }

    /**
     * 获取时区的偏移量
     */
    public static String getTimeZoneOffset(String timeZoneId) {
        TimeZone timeZone = TimeZone.getTimeZone(timeZoneId);
        int offset = timeZone.getRawOffset();
        int hours = Math.abs(offset) / (60 * 60 * 1000);
        int minutes = Math.abs(offset) / (60 * 1000) % 60;
        return String.format("%s%02d:%02d", offset >= 0 ? "+" : "-", hours, minutes);
    }

    /**
     * 检查时区是否使用夏令时
     */
    public static boolean usesDaylightTime(String timeZoneId) {
        TimeZone timeZone = TimeZone.getTimeZone(timeZoneId);
        return timeZone.useDaylightTime();
    }
} 