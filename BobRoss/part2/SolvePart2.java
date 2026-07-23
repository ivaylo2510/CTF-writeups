import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SolvePart2 {

    // ================= CONFIGURATION =================
    private static final String GALLERIES_DIR = "galleries";
    private static final String KEYS_FILE = "secretKeys.txt";      // public.xml (ID -> Name)
    private static final String VALUES_FILE = "secretValues.txt";  // strings.xml (Name -> Value)

    // Target hash from GuessingActivity.verify()
    private static final String TARGET_HASH =
            "E0ABEF650524206639147EF7EA4644321D39153A16830F5DD57BCE6485282D04"
                    .toLowerCase();
    // =================================================

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    public static void main(String[] args) {
        try {
            Map<String, String> idToName = loadKeys(KEYS_FILE);
            Map<String, String> nameToValue = loadValues(VALUES_FILE);

            File dir = new File(GALLERIES_DIR);
            if (!dir.exists()) {
                System.out.println("[!] Directory not found: " + dir.getAbsolutePath());
                return;
            }

            System.out.println("[*] Scanning for match to hash: " + TARGET_HASH);
            scanAndPrint(dir, idToName, nameToValue);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void scanAndPrint(File dir,
                                     Map<String, String> idToName,
                                     Map<String, String> nameToValue) {

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanAndPrint(file, idToName, nameToValue);
            } else if (file.getName().endsWith(".smali")) {
                processFile(file, idToName, nameToValue);
            }
        }
    }

    private static void processFile(File file,
                                    Map<String, String> idToName,
                                    Map<String, String> nameToValue) {
        try {
            String content = new String(
                    Files.readAllBytes(file.toPath()),
                    StandardCharsets.UTF_8
            );

            // A. Extract Class Name (this is the gallery "name")
            Pattern nameP = Pattern.compile("name:Ljava/lang/String;\\s*=\\s*\"([^\"]+)\"");
            Matcher nameM = nameP.matcher(content);
            if (!nameM.find()) return;
            String className = nameM.group(1);

            // B. Extract Hex ID (0x...) for the secret
            Pattern idP = Pattern.compile("const\\s+[vp]\\d+,\\s*(0x[0-9a-fA-F]+)");
            Matcher idM = idP.matcher(content);
            if (!idM.find()) return;
            String hexId = idM.group(1).toLowerCase();

            // C. Double Lookup: ID -> resource name -> secret value
            if (idToName.containsKey(hexId)) {
                String resName = idToName.get(hexId);

                if (nameToValue.containsKey(resName)) {
                    String secretValue = nameToValue.get(resName);

                    // D. Compute ID hash (used by GuessingActivity.verify)
                    String hash = computeIdHash(className, secretValue);

                    // E. Compare with Target
                    if (hash.equals(TARGET_HASH)) {
                        // Compute the real flag for THIS (correct) picture
                        String flag = computeFlag(className, secretValue);

                        System.out.println("\n################ MATCH FOUND ################");
                        System.out.println("File:         " + file.getName());
                        System.out.println("Class Name:   " + className);
                        System.out.println("Secret Value: " + secretValue);
                        System.out.println("Hash (ID):    " + hash);
                        System.out.println("Flag:         " + flag);
                        System.out.println("#############################################\n");

                        // Optional: stop after first match
                        // System.exit(0);
                    }
                }
            }

        } catch (Exception e) {
            // ignore for now
        }
    }

    // ID used in GuessingActivity.verify(): SHA-256(name || secret)
    private static String computeIdHash(String name, String secret) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(name.getBytes("UTF-8"));
            md.update(secret.getBytes("UTF-8"));
            byte[] digest = md.digest();
            return bytesToHex(digest).toLowerCase();
        } catch (Exception e) {
            return "";
        }
    }

    // Flag shown under the image:
    // SHA-256("yesthisone" || name || secret) -> first 12 hex chars -> FLG_PT2{your_flag_is_XXXX}
    private static String computeFlag(String name, String secret) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update("yesthisone".getBytes("UTF-8"));
            md.update(name.getBytes("UTF-8"));
            md.update(secret.getBytes("UTF-8"));
            String full = bytesToHex(md.digest()).toLowerCase();
            String first12 = full.substring(0, 12);
            return "FLG_PT2{your_flag_is_" + first12 + "}";
        } catch (Exception e) {
            return "";
        }
    }

    public static String bytesToHex(byte[] bArr) {
        char[] cArr = new char[bArr.length * 2];
        for (int i2 = 0; i2 < bArr.length; i2++) {
            byte b2 = bArr[i2];
            int i3 = i2 * 2;
            char[] cArr2 = HEX_ARRAY;
            cArr[i3]     = cArr2[(b2 & 0xFF) >>> 4];
            cArr[i3 + 1] = cArr2[b2 & 0x0F];
        }
        return new String(cArr);
    }

    // Load public.xml (ID -> Name)
    private static Map<String, String> loadKeys(String filepath) throws IOException {
        Map<String, String> map = new HashMap<>();
        File f = new File(filepath);
        if (!f.exists()) return map;

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            Pattern p = Pattern.compile("name=\"([^\"]+)\"[^>]*id=\"(0x[0-9a-fA-F]+)\"");
            while ((line = br.readLine()) != null) {
                Matcher m = p.matcher(line);
                if (m.find()) {
                    map.put(m.group(2).toLowerCase(), m.group(1));
                }
            }
        }
        System.out.println("[*] Loaded " + map.size() + " IDs from " + filepath);
        return map;
    }

    // Load strings.xml (Name -> Value)
    private static Map<String, String> loadValues(String filepath) throws IOException {
        Map<String, String> map = new HashMap<>();
        File f = new File(filepath);
        if (!f.exists()) return map;

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            Pattern p = Pattern.compile("<string name=\"([^\"]+)\">([^<]+)</string>");
            while ((line = br.readLine()) != null) {
                Matcher m = p.matcher(line);
                if (m.find()) {
                    map.put(m.group(1), m.group(2));
                }
            }
        }
        System.out.println("[*] Loaded " + map.size() + " Values from " + filepath);
        return map;
    }
}
