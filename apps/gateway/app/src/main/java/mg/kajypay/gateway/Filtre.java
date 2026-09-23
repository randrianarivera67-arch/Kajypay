package mg.kajypay.gateway;

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Filtre {
    public static final class Res {
        public final String operateur;
        public final long montant;
        Res(String o, long m) { operateur = o; montant = m; }
    }
    private static final Pattern ORANGE = Pattern.compile("recu un transfert de\\s*([\\d\\s.,]+?)\\s*Ar\\s+venant du\\s*(\\+?\\d[\\d\\s]{7,14}\\d)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MVOLA = Pattern.compile("^\\s*([\\d\\s.,]+?)\\s*Ar\\s+recu de\\s+(.+?)\\s+(\\+?\\d{9,12})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern AIRTEL = Pattern.compile("recu\\s+Ar\\s*([\\d\\s.,]+?)\\s+de\\s+(.+?)\\s+(\\+?\\d{9,12})\\b", Pattern.CASE_INSENSITIVE);

    static String normaliser(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("\\s+", " ").trim();
    }
    static long montant(String s) {
        try { return Math.round(Double.parseDouble(s.replaceAll("\\s", "").replace(',', '.'))); } catch (Exception e) { return 0; }
    }
    public static Res analyser(String texte) {
        String t = normaliser(texte);
        Object[][] modeles = {{"orange", ORANGE}, {"mvola", MVOLA}, {"airtel", AIRTEL}};
        for (Object[] m : modeles) {
            Matcher x = ((Pattern) m[1]).matcher(t);
            if (x.find()) {
                long v = montant(x.group(1));
                if (v > 0) return new Res((String) m[0], v);
            }
        }
        return null;
    }
}
