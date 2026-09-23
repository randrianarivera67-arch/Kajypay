package mg.kajypay.gateway;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public final class Journal extends SQLiteOpenHelper {
    public static final class Ligne {
        public long id, recuLe, montant;
        public int slot;
        public String expediteur, texte, operateur, statut, raison;
        public boolean facture;
    }
    private static final String COLS = "id, slot, expediteur, texte, recu_le, operateur, montant, statut, raison, facture";

    public Journal(Context c) { super(c.getApplicationContext(), "journal.db", null, 1); }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sms (id INTEGER PRIMARY KEY AUTOINCREMENT, slot INTEGER NOT NULL DEFAULT 0, expediteur TEXT, texte TEXT NOT NULL, recu_le INTEGER NOT NULL, operateur TEXT, montant INTEGER NOT NULL DEFAULT 0, statut TEXT NOT NULL DEFAULT 'attente', raison TEXT, facture INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_sms_statut ON sms(statut, id)");
        db.execSQL("CREATE INDEX idx_sms_slot ON sms(slot, recu_le)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int a, int b) { }

    public long ajouter(int slot, String exp, String texte, long recuLe, String op, long montant) {
        ContentValues v = new ContentValues();
        v.put("slot", slot);
        v.put("expediteur", exp);
        v.put("texte", texte);
        v.put("recu_le", recuLe);
        v.put("operateur", op);
        v.put("montant", montant);
        return getWritableDatabase().insert("sms", null, v);
    }

    private List<Ligne> lire(Cursor c) {
        List<Ligne> out = new ArrayList<>();
        try {
            while (c.moveToNext()) {
                Ligne l = new Ligne();
                l.id = c.getLong(0); l.slot = c.getInt(1); l.expediteur = c.getString(2); l.texte = c.getString(3);
                l.recuLe = c.getLong(4); l.operateur = c.getString(5); l.montant = c.getLong(6);
                l.statut = c.getString(7); l.raison = c.getString(8); l.facture = c.getInt(9) == 1;
                out.add(l);
            }
        } finally { c.close(); }
        return out;
    }

    public List<Ligne> enAttente(int max) {
        return lire(getReadableDatabase().rawQuery("SELECT " + COLS + " FROM sms WHERE statut = 'attente' ORDER BY id LIMIT " + max, null));
    }

    public List<Ligne> derniers(int slot, int max) {
        return lire(getReadableDatabase().rawQuery("SELECT " + COLS + " FROM sms WHERE slot = ? ORDER BY recu_le DESC LIMIT " + max, new String[]{String.valueOf(slot)}));
    }

    public void maj(long id, String statut, String raison, int slot, boolean facture) {
        ContentValues v = new ContentValues();
        v.put("statut", statut);
        v.put("raison", raison);
        v.put("facture", facture ? 1 : 0);
        if (slot > 0) v.put("slot", slot);
        getWritableDatabase().update("sms", v, "id = ?", new String[]{String.valueOf(id)});
    }

    public long[] jour(int slot, long debut) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*), COALESCE(SUM(montant), 0) FROM sms WHERE slot = ? AND statut = 'reconnu' AND recu_le >= ?", new String[]{String.valueOf(slot), String.valueOf(debut)});
        try { return c.moveToFirst() ? new long[]{c.getLong(0), c.getLong(1)} : new long[]{0, 0}; } finally { c.close(); }
    }

    public long enAttenteTotal() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM sms WHERE statut = 'attente'", null);
        try { return c.moveToFirst() ? c.getLong(0) : 0; } finally { c.close(); }
    }

    public void vider() { getWritableDatabase().delete("sms", null, null); }
}
