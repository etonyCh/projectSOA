package com.biblio.data;

import com.biblio.model.Emprunt;
import com.biblio.model.Livre;

import java.sql.*;
import org.mindrot.jbcrypt.BCrypt;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class Database {
    // ⚠️ CONFIGURATION MYSQL
    private static final String URL = "jdbc:mysql://localhost:3306/biblio_soa?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String USER = "root";
    private static final String PASSWORD = "";

    public static void initialize() {
        try { Class.forName("com.mysql.cj.jdbc.Driver"); } catch (ClassNotFoundException e) {}

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS livres (id INT PRIMARY KEY AUTO_INCREMENT, titre VARCHAR(255), auteur VARCHAR(255), categorie VARCHAR(255), disponible TINYINT(1))");
            stmt.execute("CREATE TABLE IF NOT EXISTS emprunts (id INT PRIMARY KEY AUTO_INCREMENT, livre_id INT, etudiant_id INT, date_emprunt VARCHAR(50), date_retour VARCHAR(50), actif TINYINT(1), pending_return TINYINT(1) DEFAULT 0)");
            stmt.execute("CREATE TABLE IF NOT EXISTS etudiants (id INT PRIMARY KEY AUTO_INCREMENT, nom VARCHAR(100), cin VARCHAR(20) UNIQUE, password VARCHAR(100))");
            
            // Migration: si la table contient encore la colonne email, la renommer en cin
            try {
                stmt.execute("ALTER TABLE etudiants CHANGE email cin VARCHAR(20)");
            } catch (SQLException ignore) {
                // Colonne n'existe pas ou déjà migrée
            }
            
            // Migration: ajouter pending_return à la table emprunts si elle n'existe pas
            try {
                stmt.execute("ALTER TABLE emprunts ADD COLUMN pending_return TINYINT(1) DEFAULT 0");
            } catch (SQLException ignore) {
                // Colonne existe déjà
            }

            stmt.execute("CREATE TABLE IF NOT EXISTS reservations (id INT PRIMARY KEY AUTO_INCREMENT, livre_id INT, etudiant_id INT, date_resa VARCHAR(50), statut VARCHAR(20))");
            stmt.execute("CREATE TABLE IF NOT EXISTS admins (id INT PRIMARY KEY AUTO_INCREMENT, username VARCHAR(50), password VARCHAR(255))");

            // Ensure admins.password column is large enough (ALTER harmless if same)
            try {
                stmt.execute("ALTER TABLE admins MODIFY COLUMN password VARCHAR(255)");
            } catch (SQLException ignore) {}

            // Admin par défaut (mot de passe haché). If an admin exists but password seems corrupted,
            // reset it to a valid bcrypt hash of 'admin' to avoid locking out development admin.
            ResultSet rs = stmt.executeQuery("SELECT count(*) FROM admins");
            if(rs.next() && rs.getInt(1) == 0) {
                String hashed = BCrypt.hashpw("admin", BCrypt.gensalt());
                try (PreparedStatement pa = conn.prepareStatement("INSERT INTO admins(username, password) VALUES(?,?)")) {
                    pa.setString(1, "admin"); pa.setString(2, hashed); pa.executeUpdate();
                }
            } else {
                try (PreparedStatement p = conn.prepareStatement("SELECT password FROM admins WHERE username=?")) {
                    p.setString(1, "admin");
                    ResultSet r2 = p.executeQuery();
                    if (r2.next()) {
                        String stored = r2.getString("password");
                        boolean ok = false;
                        if (stored != null && (stored.startsWith("$2a$") || stored.startsWith("$2y$") || stored.startsWith("$2b$"))) ok = true;
                        if (!ok && "admin".equals(stored)) ok = true;
                        if (!ok) {
                            String hashed = BCrypt.hashpw("admin", BCrypt.gensalt());
                            try (PreparedStatement up = conn.prepareStatement("UPDATE admins SET password=? WHERE username=?")) { up.setString(1, hashed); up.setString(2, "admin"); up.executeUpdate(); }
                        }
                    }
                } catch (SQLException ignore) {}
            }
            
            System.out.println("✅ Base de données MySQL prête.");
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    // --- ADMIN ---
    public static boolean checkAdminLogin(String u, String p) {
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT password FROM admins WHERE username=?")) {
            ps.setString(1, u);
            ResultSet rs = ps.executeQuery();
            if(rs.next()) {
                String stored = rs.getString("password");
                if(stored != null && (stored.startsWith("$2a$") || stored.startsWith("$2y$") || stored.startsWith("$2b$"))) {
                    return BCrypt.checkpw(p, stored);
                } else {
                    // legacy plaintext (rare) - compare and migrate
                    if(p.equals(stored)) {
                        String h = BCrypt.hashpw(p, BCrypt.gensalt());
                        try (PreparedStatement up = conn.prepareStatement("UPDATE admins SET password=? WHERE username=?")) { up.setString(1, h); up.setString(2, u); up.executeUpdate(); }
                        return true;
                    }
                }
            }
            return false;
        } catch (SQLException e) { return false; }
    }

    // --- LIVRES ---
    public static List<Livre> getLivres() {
        List<Livre> l = new ArrayList<>();
        try (Connection conn = getConnection(); ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM livres")) {
            while(rs.next()) l.add(new Livre(rs.getInt("id"), rs.getString("titre"), rs.getString("auteur"), rs.getString("categorie"), rs.getInt("disponible")==1));
        } catch (SQLException e) {} return l;
    }
    public static void addLivre(String t, String a, String c) {
        try (Connection conn = getConnection(); PreparedStatement p = conn.prepareStatement("INSERT INTO livres(titre, auteur, categorie, disponible) VALUES(?,?,?,1)")) {
            p.setString(1, t); p.setString(2, a); p.setString(3, c); p.executeUpdate();
        } catch (SQLException e) {}
    }
    public static void updateLivre(int id, String t, String a, String c) {
        try (Connection conn = getConnection(); PreparedStatement p = conn.prepareStatement("UPDATE livres SET titre=?, auteur=?, categorie=? WHERE id=?")) {
            p.setString(1, t); p.setString(2, a); p.setString(3, c); p.setInt(4, id); p.executeUpdate();
        } catch (SQLException e) {}
    }
    public static boolean supprimerLivre(int id) {
        try (Connection conn = getConnection(); PreparedStatement p = conn.prepareStatement("DELETE FROM livres WHERE id=?")) {
            p.setInt(1, id); return p.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }
    
    // --- ETUDIANTS ---
    public static int checkLogin(String cin, String password) {
        try (Connection conn = getConnection(); PreparedStatement p = conn.prepareStatement("SELECT id, password FROM etudiants WHERE cin=?")) {
            p.setString(1, cin);
            ResultSet rs = p.executeQuery();
            if (rs.next()) {
                int id = rs.getInt("id");
                String stored = rs.getString("password");
                if (stored != null && (stored.startsWith("$2a$") || stored.startsWith("$2y$") || stored.startsWith("$2b$"))) {
                    if (BCrypt.checkpw(password, stored)) return id;
                } else {
                    // legacy plaintext: compare, then migrate to bcrypt
                    if (password.equals(stored)) {
                        String hash = BCrypt.hashpw(password, BCrypt.gensalt());
                        try (PreparedStatement up = conn.prepareStatement("UPDATE etudiants SET password=? WHERE id=?")) { up.setString(1, hash); up.setInt(2, id); up.executeUpdate(); }
                        return id;
                    }
                }
            }
        } catch (SQLException e) {}
        return -1;
    }
    public static void addEtudiant(String n, String cin, String p) {
        String hash = BCrypt.hashpw(p, BCrypt.gensalt());
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement("INSERT INTO etudiants(nom, cin, password) VALUES(?,?,?)")) {
            ps.setString(1, n); ps.setString(2, cin); ps.setString(3, hash); ps.executeUpdate();
        } catch (SQLException ex) {}
    }

    // Met à jour un étudiant (nom, cin, mot de passe optionnel)
    public static boolean updateEtudiant(int id, String nom, String cin, String password) {
        try (Connection conn = getConnection()) {
            if (password != null && !password.isEmpty()) {
                String hash = BCrypt.hashpw(password, BCrypt.gensalt());
                try (PreparedStatement ps = conn.prepareStatement("UPDATE etudiants SET nom=?, cin=?, password=? WHERE id=?")) {
                    ps.setString(1, nom); ps.setString(2, cin); ps.setString(3, hash); ps.setInt(4, id); ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement("UPDATE etudiants SET nom=?, cin=? WHERE id=?")) {
                    ps.setString(1, nom); ps.setString(2, cin); ps.setInt(3, id); ps.executeUpdate();
                }
            }
            return true;
        } catch (SQLException e) { return false; }
    }

    // Change password (vérifie l'ancien mot de passe puis met à jour)
    public static boolean changePassword(int id, String oldPass, String newPass) {
        try (Connection conn = getConnection(); PreparedStatement p = conn.prepareStatement("SELECT password FROM etudiants WHERE id=?")) {
            p.setInt(1, id);
            ResultSet rs = p.executeQuery();
            if (rs.next()) {
                String stored = rs.getString("password");
                boolean ok = false;
                if (stored != null && (stored.startsWith("$2a$") || stored.startsWith("$2y$") || stored.startsWith("$2b$"))) {
                    ok = BCrypt.checkpw(oldPass, stored);
                } else {
                    ok = oldPass.equals(stored);
                }
                if (ok) {
                    String hash = BCrypt.hashpw(newPass, BCrypt.gensalt());
                    try (PreparedStatement up = conn.prepareStatement("UPDATE etudiants SET password=? WHERE id=?")) { up.setString(1, hash); up.setInt(2, id); up.executeUpdate(); }
                    return true;
                }
            }
        } catch (SQLException e) {}
        return false;
    }
    public static void deleteEtudiant(int id) {
        try (Connection conn = getConnection(); PreparedStatement p = conn.prepareStatement("DELETE FROM etudiants WHERE id=?")) {
            p.setInt(1, id); p.executeUpdate();
        } catch (SQLException e) {}
    }
    public static List<String[]> getListeEtudiants() {
        List<String[]> list = new ArrayList<>();
        try (Connection conn = getConnection(); ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM etudiants")) {
            while (rs.next()) list.add(new String[]{String.valueOf(rs.getInt("id")), rs.getString("nom"), rs.getString("cin")});
        } catch (SQLException e) {} return list;
    }

    // Retourne les infos d'un étudiant par id (id, nom, cin) ou null si introuvable
    public static String[] getEtudiantById(int id) {
        try (Connection conn = getConnection(); PreparedStatement p = conn.prepareStatement("SELECT * FROM etudiants WHERE id=?")) {
            p.setInt(1, id);
            ResultSet rs = p.executeQuery();
            if (rs.next()) return new String[]{String.valueOf(rs.getInt("id")), rs.getString("nom"), rs.getString("cin")};
        } catch (SQLException e) {}
        return null;
    }

    // --- EMPRUNTS ---
    public static void creerEmprunt(int lid, int eid, String date) {
        try (Connection conn = getConnection()) {
            try (PreparedStatement p = conn.prepareStatement("INSERT INTO emprunts(livre_id, etudiant_id, date_emprunt, actif, pending_return) VALUES(?,?,?,1,0)")) {
                p.setInt(1, lid); p.setInt(2, eid); p.setString(3, date); p.executeUpdate();
            }
            try (PreparedStatement p = conn.prepareStatement("UPDATE livres SET disponible=0 WHERE id=?")) {
                p.setInt(1, lid); p.executeUpdate();
            }
        } catch (SQLException e) {}
    }
    public static List<Emprunt> getEmpruntsActifs() {
        List<Emprunt> l = new ArrayList<>();
        try (Connection conn = getConnection(); ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM emprunts WHERE actif=1")) {
            while(rs.next()) l.add(new Emprunt(rs.getInt("id"), rs.getInt("livre_id"), rs.getInt("etudiant_id"), rs.getString("date_emprunt"), rs.getInt("pending_return")==1));
        } catch (SQLException e) {} return l;
    }
    public static List<Emprunt> getEmpruntsByEtudiant(int eid) {
        List<Emprunt> l = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement p = conn.prepareStatement("SELECT * FROM emprunts WHERE etudiant_id=? AND actif=1")) {
            p.setInt(1, eid); ResultSet rs = p.executeQuery();
            while(rs.next()) l.add(new Emprunt(rs.getInt("id"), rs.getInt("livre_id"), rs.getInt("etudiant_id"), rs.getString("date_emprunt"), rs.getInt("pending_return")==1));
        } catch (SQLException e) {} return l;
    }
    public static List<String[]> getHistoriqueEmprunts() {
        List<String[]> list = new ArrayList<>();
        try (Connection conn = getConnection(); ResultSet rs = conn.createStatement().executeQuery(
                "SELECT e.id, l.titre, u.nom, e.date_emprunt, e.date_retour, e.actif, e.pending_return FROM emprunts e JOIN livres l ON e.livre_id=l.id JOIN etudiants u ON e.etudiant_id=u.id")) {
            while (rs.next()) {
                String status;
                int actif = rs.getInt("actif");
                int pending = rs.getInt("pending_return");
                if (actif == 0) status = "Rendu";
                else if (pending == 1) status = "Retour demandé";
                else status = "En cours";
                list.add(new String[]{String.valueOf(rs.getInt("id")), rs.getString("titre"), rs.getString("nom"), rs.getString("date_emprunt"), rs.getString("date_retour"), status});
            }
        } catch (SQLException e) {} return list;
    }
    public static String validerRetour(int eid, int lid) {
         try (Connection conn = getConnection()) {
            try(PreparedStatement p = conn.prepareStatement("UPDATE emprunts SET actif=0, date_retour=?, pending_return=0 WHERE id=?")) {
                p.setString(1, LocalDate.now().toString()); p.setInt(2, eid); p.executeUpdate();
            }
            try(PreparedStatement p = conn.prepareStatement("UPDATE livres SET disponible=1 WHERE id=?")) {
                p.setInt(1, lid); p.executeUpdate();
            }
            
            // Vérifier s'il y a des réservations en attente pour ce livre
            List<String[]> reservations = getReservationsAttente(lid);
            if (!reservations.isEmpty()) {
                // Notifier le premier étudiant en attente (FIFO)
                String[] premiereReservation = reservations.get(0);
                int reservationId = Integer.parseInt(premiereReservation[0]);
                notifierReservationDisponible(reservationId);
                String nomEtudiant = premiereReservation[1];
                String cinEtudiant = premiereReservation[2];
                return "Retour validé - " + nomEtudiant + " (" + cinEtudiant + ") notifié(e)";
            }
            
            return "Retour validé";
        } catch (SQLException e) { return "Erreur"; }
    }
    public static String validerRetour(int lid) { return validerRetour(0, lid); }

    // --- RESERVATIONS (C'était ici que ça manquait) ---
    public static void reserverLivre(int lid, int eid) {
        try (Connection conn = getConnection(); PreparedStatement p = conn.prepareStatement("INSERT INTO reservations(livre_id, etudiant_id, date_resa, statut) VALUES(?,?,?, 'WAIT')")) {
            p.setInt(1, lid); p.setInt(2, eid); p.setString(3, LocalDate.now().toString()); p.executeUpdate();
        } catch(Exception e){}
    }

    // Etudiant demande un retour (marque pending_return=1)
    public static boolean requestReturn(int empruntId) {
        try (Connection conn = getConnection(); PreparedStatement p = conn.prepareStatement("UPDATE emprunts SET pending_return=1 WHERE id=?")) {
            p.setInt(1, empruntId); return p.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }
    
    // Cette méthode manquait ou était mal fermée
    public static List<String[]> getReservations() {
        List<String[]> list = new ArrayList<>();
        try (Connection conn = getConnection(); ResultSet rs = conn.createStatement().executeQuery(
                "SELECT r.id, l.titre, u.nom, r.date_resa FROM reservations r JOIN livres l ON r.livre_id=l.id JOIN etudiants u ON r.etudiant_id=u.id")) {
            while (rs.next()) list.add(new String[]{String.valueOf(rs.getInt("id")), rs.getString("titre"), rs.getString("nom"), rs.getString("date_resa")});
        } catch (SQLException e) {} return list;
    }

    // Celle-ci aussi
    public static void deleteReservation(int id) {
         try (Connection conn = getConnection(); PreparedStatement p = conn.prepareStatement("DELETE FROM reservations WHERE id=?")) {
            p.setInt(1, id); p.executeUpdate();
        } catch (SQLException e) {}
    }

    // --- NOTIFICATIONS RESERVATIONS ---
    public static List<String[]> getReservationsAttente(int livreId) {
        List<String[]> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement p = conn.prepareStatement(
                "SELECT r.id, u.nom, u.cin FROM reservations r JOIN etudiants u ON r.etudiant_id=u.id WHERE r.livre_id=? AND r.statut='WAIT' ORDER BY r.date_resa")) {
            p.setInt(1, livreId);
            ResultSet rs = p.executeQuery();
            while (rs.next()) {
                list.add(new String[]{String.valueOf(rs.getInt("id")), rs.getString("nom"), rs.getString("cin")});
            }
        } catch (SQLException e) {}
        return list;
    }
    
    public static void notifierReservationDisponible(int reservationId) {
        try (Connection conn = getConnection(); PreparedStatement p = conn.prepareStatement(
                "UPDATE reservations SET statut='NOTIFIED' WHERE id=?")) {
            p.setInt(1, reservationId); p.executeUpdate();
        } catch (SQLException e) {}
    }

    // --- PENALITES ---
    public static List<String> getPenalites(int etudiantId) {
        List<String> penalites = new ArrayList<>();
        List<Emprunt> emprunts = getEmpruntsByEtudiant(etudiantId);
        LocalDate now = LocalDate.now();
        for (Emprunt e : emprunts) {
            if(e.getDateEmprunt() != null) {
                LocalDate dateEmprunt = LocalDate.parse(e.getDateEmprunt());
                long jours = ChronoUnit.DAYS.between(dateEmprunt, now);
                if (jours > 14) penalites.add("Retard de " + (jours - 14) + " jours (Livre ID " + e.getLivreId() + ")");
            }
        }
        return penalites;
    }

} 