import java.security.SecureRandom;

public class RandomShortCodeGenerator implements ShortCodeGenerator{
    private static final String ALPHABET =  "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SHORT_CODE_LENGTH = 6;
    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        StringBuilder shortCode = new StringBuilder();
        char c;

        for (int i = 0; i < SHORT_CODE_LENGTH; i++) {
            c = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
            shortCode.append(c);
        }

        return shortCode.toString();
    }
}
