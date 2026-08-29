public class ApplicationConfig {

    public static String getRequired(
            String name
    ) {
        String value =
                System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing required environment variable: "
                            + name
            );
        }

        return value;
    }

    public static String getOrDefault(
            String name,
            String defaultValue
    ) {
        String value =
                System.getenv(name);

        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value;
    }

    public static int getIntOrDefault(
            String name,
            int defaultValue
    ) {
        String value =
                System.getenv(name);

        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return Integer.parseInt(value);
    }
}