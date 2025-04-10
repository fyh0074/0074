import java.util.TimeZone;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.stream.Collectors;

public class TimeZoneManager {
    // 定义时区枚举
    public enum TimeZoneMapping {
        // 亚洲时区
        SHANGHAI("Asia/Shanghai", "China Standard Time"),
        BEIJING("Asia/Beijing", "China Standard Time"),
        HONG_KONG("Asia/Hong_Kong", "China Standard Time"),
        MACAU("Asia/Macau", "China Standard Time"),
        TOKYO("Asia/Tokyo", "Tokyo Standard Time"),
        SEOUL("Asia/Seoul", "Korea Standard Time"),
        TAIPEI("Asia/Taipei", "Taipei Standard Time"),
        SINGAPORE("Asia/Singapore", "Singapore Standard Time"),
        KUALA_LUMPUR("Asia/Kuala_Lumpur", "Singapore Standard Time"),
        BANGKOK("Asia/Bangkok", "SE Asia Standard Time"),
        MANILA("Asia/Manila", "Singapore Standard Time"),
        JAKARTA("Asia/Jakarta", "SE Asia Standard Time"),
        HO_CHI_MINH("Asia/Ho_Chi_Minh", "SE Asia Standard Time"),
        KOLKATA("Asia/Kolkata", "India Standard Time"),
        MUMBAI("Asia/Mumbai", "India Standard Time"),
        NEW_DELHI("Asia/New_Delhi", "India Standard Time"),
        DUBAI("Asia/Dubai", "Arabian Standard Time"),
        ABU_DHABI("Asia/Abu_Dhabi", "Arabian Standard Time"),
        RIYADH("Asia/Riyadh", "Arab Standard Time"),
        DOHA("Asia/Qatar", "Arab Standard Time"),
        TEL_AVIV("Asia/Tel_Aviv", "Israel Standard Time"),
        BAGHDAD("Asia/Baghdad", "Arabic Standard Time"),
        TEHRAN("Asia/Tehran", "Iran Standard Time"),
        KARACHI("Asia/Karachi", "Pakistan Standard Time"),
        DHAKA("Asia/Dhaka", "Bangladesh Standard Time"),
        YANGON("Asia/Yangon", "Myanmar Standard Time"),
        ULAANBAATAR("Asia/Ulaanbaatar", "Ulaanbaatar Standard Time"),
        
        // 美洲时区
        NEW_YORK("America/New_York", "Eastern Standard Time"),
        CHICAGO("America/Chicago", "Central Standard Time"),
        DENVER("America/Denver", "Mountain Standard Time"),
        LOS_ANGELES("America/Los_Angeles", "Pacific Standard Time"),
        PHOENIX("America/Phoenix", "US Mountain Standard Time"),
        ANCHORAGE("America/Anchorage", "Alaskan Standard Time"),
        HONOLULU("Pacific/Honolulu", "Hawaiian Standard Time"),
        TORONTO("America/Toronto", "Eastern Standard Time"),
        VANCOUVER("America/Vancouver", "Pacific Standard Time"),
        MEXICO_CITY("America/Mexico_City", "Central Standard Time (Mexico)"),
        LIMA("America/Lima", "SA Pacific Standard Time"),
        BOGOTA("America/Bogota", "SA Pacific Standard Time"),
        SANTIAGO("America/Santiago", "Pacific SA Standard Time"),
        BUENOS_AIRES("America/Argentina/Buenos_Aires", "Argentina Standard Time"),
        SAO_PAULO("America/Sao_Paulo", "E. South America Standard Time"),
        RIO_DE_JANEIRO("America/Rio_de_Janeiro", "E. South America Standard Time"),
        
        // 欧洲时区
        LONDON("Europe/London", "GMT Standard Time"),
        PARIS("Europe/Paris", "Romance Standard Time"),
        BERLIN("Europe/Berlin", "W. Europe Standard Time"),
        ROME("Europe/Rome", "W. Europe Standard Time"),
        MADRID("Europe/Madrid", "Romance Standard Time"),
        AMSTERDAM("Europe/Amsterdam", "W. Europe Standard Time"),
        BRUSSELS("Europe/Brussels", "Romance Standard Time"),
        VIENNA("Europe/Vienna", "W. Europe Standard Time"),
        ZURICH("Europe/Zurich", "W. Europe Standard Time"),
        STOCKHOLM("Europe/Stockholm", "W. Europe Standard Time"),
        OSLO("Europe/Oslo", "W. Europe Standard Time"),
        COPENHAGEN("Europe/Copenhagen", "Romance Standard Time"),
        HELSINKI("Europe/Helsinki", "FLE Standard Time"),
        PRAGUE("Europe/Prague", "Central Europe Standard Time"),
        WARSAW("Europe/Warsaw", "Central European Standard Time"),
        BUDAPEST("Europe/Budapest", "Central Europe Standard Time"),
        BUCHAREST("Europe/Bucharest", "GTB Standard Time"),
        MOSCOW("Europe/Moscow", "Russian Standard Time"),
        KIEV("Europe/Kiev", "FLE Standard Time"),
        ATHENS("Europe/Athens", "GTB Standard Time"),
        LISBON("Europe/Lisbon", "GMT Standard Time"),
        DUBLIN("Europe/Dublin", "GMT Standard Time"),
        
        // 大洋洲时区
        SYDNEY("Australia/Sydney", "AUS Eastern Standard Time"),
        MELBOURNE("Australia/Melbourne", "AUS Eastern Standard Time"),
        BRISBANE("Australia/Brisbane", "E. Australia Standard Time"),
        ADELAIDE("Australia/Adelaide", "Cen. Australia Standard Time"),
        PERTH("Australia/Perth", "W. Australia Standard Time"),
        DARWIN("Australia/Darwin", "AUS Central Standard Time"),
        AUCKLAND("Pacific/Auckland", "New Zealand Standard Time"),
        WELLINGTON("Pacific/Auckland", "New Zealand Standard Time"),
        FIJI("Pacific/Fiji", "Fiji Standard Time"),
        GUAM("Pacific/Guam", "West Pacific Standard Time"),
        
        // 非洲时区
        CAIRO("Africa/Cairo", "Egypt Standard Time"),
        JOHANNESBURG("Africa/Johannesburg", "South Africa Standard Time"),
        CAPE_TOWN("Africa/Johannesburg", "South Africa Standard Time"),
        LAGOS("Africa/Lagos", "W. Central Africa Standard Time"),
        NAIROBI("Africa/Nairobi", "E. Africa Standard Time"),
        CASABLANCA("Africa/Casablanca", "Morocco Standard Time"),
        TUNIS("Africa/Tunis", "W. Central Africa Standard Time"),
        ALGIERS("Africa/Algiers", "W. Central Africa Standard Time"),
        TRIPOLI("Africa/Tripoli", "Libya Standard Time"),
        KHARTOUM("Africa/Khartoum", "E. Africa Standard Time");
        
        private final String javaId;
        private final String windowsId;
        
        TimeZoneMapping(String javaId, String windowsId) {
            this.javaId = javaId;
            this.windowsId = windowsId;
        }
        
        public String getJavaId() {
            return javaId;
        }
        
        public String getWindowsId() {
            return windowsId;
        }
    }
    
    // 使用EnumMap存储时区映射
    private static final Map<String, TimeZoneMapping> JAVA_ID_MAP = new HashMap<>();
    private static final Map<String, TimeZoneMapping> WINDOWS_ID_MAP = new HashMap<>();
    
    static {
        // 初始化映射
        for (TimeZoneMapping mapping : TimeZoneMapping.values()) {
            JAVA_ID_MAP.put(mapping.getJavaId(), mapping);
            WINDOWS_ID_MAP.put(mapping.getWindowsId(), mapping);
        }
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
     * 获取所有支持的时区映射
     */
    public static List<TimeZoneMapping> getAllSupportedTimeZones() {
        return List.of(TimeZoneMapping.values());
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
     * @param javaTimeZoneId Java时区ID，例如："Asia/Shanghai"
     */
    public static void setTimeZone(String javaTimeZoneId) {
        try {
            TimeZoneMapping mapping = JAVA_ID_MAP.get(javaTimeZoneId);
            if (mapping != null) {
                Process process = Runtime.getRuntime().exec("tzutil /s \"" + mapping.getWindowsId() + "\"");
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    System.out.println("Successfully changed timezone to: " + javaTimeZoneId);
                } else {
                    System.err.println("Failed to change timezone. Exit code: " + exitCode);
                }
            } else {
                System.err.println("Unsupported timezone: " + javaTimeZoneId);
            }
        } catch (Exception e) {
            System.err.println("Error changing timezone: " + e.getMessage());
        }
    }
    
    /**
     * 获取时区的显示名称
     */
    public static String getDisplayName(String javaTimeZoneId) {
        TimeZoneMapping mapping = JAVA_ID_MAP.get(javaTimeZoneId);
        return mapping != null ? mapping.getWindowsId() : "Unknown Timezone";
    }
    
    /**
     * 检查时区是否受支持
     */
    public static boolean isSupported(String javaTimeZoneId) {
        return JAVA_ID_MAP.containsKey(javaTimeZoneId);
    }
    
    /**
     * 按地区获取时区列表
     */
    public static List<TimeZoneMapping> getTimeZonesByRegion(String region) {
        return Arrays.stream(TimeZoneMapping.values())
                .filter(mapping -> mapping.name().startsWith(region.toUpperCase()))
                .collect(Collectors.toList());
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

    /**
     * 验证Windows时区ID是否有效
     * @param windowsId Windows时区ID
     * @return 是否有效
     */
    public static boolean isValidWindowsTimeZone(String windowsId) {
        try {
            // 使用tzutil命令验证时区ID
            Process process = Runtime.getRuntime().exec("tzutil /l");
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().equals(windowsId)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            System.err.println("Error validating timezone: " + e.getMessage());
            return false;
        }
    }

    /**
     * 验证所有Windows时区ID的有效性
     */
    public static void validateAllWindowsTimeZones() {
        System.out.println("Validating Windows time zone IDs...");
        int validCount = 0;
        int invalidCount = 0;
        
        for (TimeZoneMapping mapping : TimeZoneMapping.values()) {
            String windowsId = mapping.getWindowsId();
            boolean isValid = isValidWindowsTimeZone(windowsId);
            System.out.printf("%-40s %-30s %s%n", 
                mapping.name(), 
                windowsId, 
                isValid ? "VALID" : "INVALID");
            
            if (isValid) {
                validCount++;
            } else {
                invalidCount++;
            }
        }
        
        System.out.println("\nValidation Summary:");
        System.out.println("Total time zones: " + TimeZoneMapping.values().length);
        System.out.println("Valid time zones: " + validCount);
        System.out.println("Invalid time zones: " + invalidCount);
    }
} 