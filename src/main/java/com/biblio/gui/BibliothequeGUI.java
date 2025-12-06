package com.biblio.gui;

import com.biblio.data.Database;
import com.biblio.model.Emprunt;
import com.biblio.model.Livre;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class BibliothequeGUI extends JFrame {
    
    // --- Modern Color Palette ---
    private static final Color COL_PRIMARY = new Color(79, 70, 229);   // Indigo 600
    private static final Color COL_SECONDARY = new Color(100, 116, 139); // Slate 500
    private static final Color COL_BG = new Color(243, 244, 246);      // Gray 100
    private static final Color COL_SURFACE = Color.WHITE;
    private static final Color COL_TEXT = new Color(31, 41, 55);       // Gray 800
    private static final Color COL_TEXT_MUTED = new Color(107, 114, 128); // Gray 500
    private static final Color COL_DANGER = new Color(239, 68, 68);    // Red 500
    private static final Color COL_SUCCESS = new Color(16, 185, 129);  // Emerald 500
    
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 24);
    private static final Font FONT_HEADER = new Font("Segoe UI", Font.BOLD, 14);
    private static final Font FONT_NORMAL = new Font("Segoe UI", Font.PLAIN, 13);

    private CardLayout mainCardLayout = new CardLayout();
    private JPanel mainPanel = new JPanel(mainCardLayout);
    
    // Login Components
    private JTextField txtUser = new JTextField(15);
    private JPasswordField txtPass = new JPasswordField(15);
    
    // App Components
    private CardLayout contentCardLayout = new CardLayout();
    private JPanel contentPanel = new JPanel(contentCardLayout);
    private JPanel sidebarPanel;
    
    private JTable tableLivres, tableEtudiants, tableEmprunts, tableReservations;
    private DefaultTableModel modelLivres, modelEtudiants, modelEmprunts, modelReservations;
    
    private Timer refreshTimer;

    public BibliothequeGUI() {
        // Setup Look and Feel
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) { /* ignore */ }
        
        // Global UI Tweaks
        UIManager.put("Table.font", FONT_NORMAL);
        UIManager.put("TableHeader.font", FONT_HEADER);
        UIManager.put("Button.font", new Font("Segoe UI", Font.BOLD, 12));

        setTitle("Système de Gestion de Bibliothèque (SOA Admin)");
        setSize(1100, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        // --- LOGIN SCREEN ---
        JPanel loginScreen = createLoginScreen();
        
        // --- APP SCREEN ---
        JPanel appScreen = createAppScreen();

        mainPanel.add(loginScreen, "LOGIN");
        mainPanel.add(appScreen, "APP");
        add(mainPanel);
    }

    private JPanel createLoginScreen() {
        // Gradient Background
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                GradientPaint gp = new GradientPaint(0, 0, COL_PRIMARY, getWidth(), getHeight(), new Color(129, 140, 248));
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        panel.setLayout(new GridBagLayout());

        // Card Panel
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(COL_SURFACE);
        card.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));
        
        // Title
        JLabel title = new JLabel("📚 Bibliothèque");
        title.setFont(FONT_TITLE);
        title.setForeground(COL_PRIMARY);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        // Inputs
        JPanel fields = new JPanel(new GridLayout(4, 1, 10, 10));
        fields.setBackground(COL_SURFACE);
        fields.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));
        
        JLabel l1 = new JLabel("Identifiant Admin"); l1.setFont(FONT_NORMAL);
        JLabel l2 = new JLabel("Mot de passe"); l2.setFont(FONT_NORMAL);
        
        styleInput(txtUser);
        styleInput(txtPass);

        fields.add(l1); fields.add(txtUser);
        fields.add(l2); fields.add(txtPass);
        fields.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Button
        JButton btnLogin = new StyledButton("Se connecter", COL_PRIMARY, Color.WHITE);
        btnLogin.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnLogin.setMaximumSize(new Dimension(200, 40));
        
        btnLogin.addActionListener(e -> performLogin());
        txtPass.addActionListener(e -> performLogin()); // Enter key

        card.add(title);
        card.add(fields);
        card.add(btnLogin);
        
        panel.add(card);
        return panel;
    }
    
    private void performLogin() {
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                return Database.checkAdminLogin(txtUser.getText(), new String(txtPass.getPassword()));
            }

            @Override
            protected void done() {
                try {
                    if (get()) {
                        mainCardLayout.show(mainPanel, "APP");
                        startAutoRefresh();
                    } else {
                        JOptionPane.showMessageDialog(BibliothequeGUI.this, "Identifiants incorrects", "Erreur", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) { ex.printStackTrace(); }
            }
        }.execute();
    }

    private JPanel createAppScreen() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // HEADER
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(COL_SURFACE);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(229, 231, 235)),
            new EmptyBorder(15, 20, 15, 20)
        ));
        
        JLabel brand = new JLabel("BiblioTech Admin");
        brand.setFont(new Font("Segoe UI", Font.BOLD, 18));
        brand.setForeground(COL_PRIMARY);
        
        JButton btnLogout = new StyledButton("Déconnexion", COL_DANGER, Color.WHITE);
        btnLogout.setPreferredSize(new Dimension(120, 30));
        btnLogout.addActionListener(e -> {
            stopAutoRefresh();
            txtPass.setText("");
            mainCardLayout.show(mainPanel, "LOGIN");
        });
        
        header.add(brand, BorderLayout.WEST);
        header.add(btnLogout, BorderLayout.EAST);
        
        // SIDEBAR
        sidebarPanel = new JPanel();
        sidebarPanel.setLayout(new BoxLayout(sidebarPanel, BoxLayout.Y_AXIS));
        sidebarPanel.setBackground(COL_SURFACE);
        sidebarPanel.setPreferredSize(new Dimension(220, 0));
        sidebarPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(229, 231, 235)));
        
        addSidebarButton("📚  Livres", "LIVRES");
        addSidebarButton("👥  Étudiants", "ETUDIANTS");
        addSidebarButton("🔄  Emprunts", "EMPRUNTS");
        addSidebarButton("📋  Réservations", "RESERVATIONS");
        
        // CONTENT
        contentPanel.setBackground(COL_BG);
        contentPanel.add(createPanelLivres(), "LIVRES");
        contentPanel.add(createPanelEtudiants(), "ETUDIANTS");
        contentPanel.add(createPanelEmprunts(), "EMPRUNTS");
        contentPanel.add(createPanelReservations(), "RESERVATIONS");
        
        panel.add(header, BorderLayout.NORTH);
        panel.add(sidebarPanel, BorderLayout.WEST);
        panel.add(contentPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    private void addSidebarButton(String text, String cardName) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setForeground(COL_TEXT_MUTED);
        btn.setBackground(COL_SURFACE);
        btn.setBorder(new EmptyBorder(15, 20, 15, 20));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setMaximumSize(new Dimension(220, 50));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        btn.addActionListener(e -> {
            contentCardLayout.show(contentPanel, cardName);
            // Reset styles
            for(Component c : sidebarPanel.getComponents()) {
                if(c instanceof JButton) {
                    c.setForeground(COL_TEXT_MUTED);
                    ((JButton)c).setFont(new Font("Segoe UI", Font.PLAIN, 14));
                }
            }
            // Active style
            btn.setForeground(COL_PRIMARY);
            btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        });
        
        sidebarPanel.add(btn);
    }

    // --- SUB-PANELS ---

    private JPanel createPanelLivres() {
        JPanel p = createContentPanel("Gestion des Livres");
        
        modelLivres = new DefaultTableModel(new String[]{"ID", "Titre", "Auteur", "Cat", "Dispo"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        tableLivres = createTable(modelLivres);
        
        // Form
        JPanel f = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        f.setBackground(COL_SURFACE);
        f.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(229, 231, 235)));
        
        JTextField t1 = new JTextField(12); styleInput(t1);
        JTextField t2 = new JTextField(12); styleInput(t2);
        JTextField t3 = new JTextField(8); styleInput(t3);
        
        f.add(new JLabel("Titre:")); f.add(t1);
        f.add(new JLabel("Auteur:")); f.add(t2);
        f.add(new JLabel("Cat:")); f.add(t3);
        
        JButton bAdd = new StyledButton("Ajouter", COL_PRIMARY, Color.WHITE);
        JButton bMod = new StyledButton("Modifier", COL_SECONDARY, Color.WHITE);
        JButton bDel = new StyledButton("Supprimer", COL_DANGER, Color.WHITE);
        
        f.add(bAdd); f.add(bMod); f.add(bDel);

        // Logic
        bAdd.addActionListener(e -> {
            String titre = t1.getText(), auteur = t2.getText(), cat = t3.getText();
            runAsync(() -> Database.addLivre(titre, auteur, cat));
        });
        
        bMod.addActionListener(e -> {
            int r = tableLivres.getSelectedRow();
            if(r>=0) {
                int id = (int)modelLivres.getValueAt(r,0);
                String titre = t1.getText(), auteur = t2.getText(), cat = t3.getText();
                runAsync(() -> Database.updateLivre(id, titre, auteur, cat));
            }
        });
        
        bDel.addActionListener(e -> {
            int r = tableLivres.getSelectedRow();
            if(r>=0) {
                int id = (int)modelLivres.getValueAt(r,0);
                runAsync(() -> Database.supprimerLivre(id));
            }
        });

        tableLivres.getSelectionModel().addListSelectionListener(e -> {
            int r = tableLivres.getSelectedRow();
            if(r>=0) {
                t1.setText(modelLivres.getValueAt(r,1).toString());
                t2.setText(modelLivres.getValueAt(r,2).toString());
                t3.setText(modelLivres.getValueAt(r,3).toString());
            }
        });

        p.add(new JScrollPane(tableLivres), BorderLayout.CENTER);
        p.add(f, BorderLayout.SOUTH);
        return p;
    }

    private JPanel createPanelEtudiants() {
        JPanel p = createContentPanel("Gestion des Étudiants");
        
        modelEtudiants = new DefaultTableModel(new String[]{"ID", "Nom", "CIN"}, 0);
        tableEtudiants = createTable(modelEtudiants);
        
        JPanel f = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        f.setBackground(COL_SURFACE);
        f.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(229, 231, 235)));
        
        JTextField tNom = new JTextField(10); styleInput(tNom);
        JTextField tCin = new JTextField(8); styleInput(tCin);
        JTextField tPass = new JTextField(8); styleInput(tPass);
        
        f.add(new JLabel("Nom:")); f.add(tNom);
        f.add(new JLabel("CIN:")); f.add(tCin);
        f.add(new JLabel("Pass:")); f.add(tPass);
        
        JButton bAdd = new StyledButton("Ajouter", COL_PRIMARY, Color.WHITE);
        JButton bMod = new StyledButton("Modifier", COL_SECONDARY, Color.WHITE);
        JButton bDel = new StyledButton("Supprimer", COL_DANGER, Color.WHITE);
        
        f.add(bAdd); f.add(bMod); f.add(bDel);

        bAdd.addActionListener(e -> {
            String nom = tNom.getText(), cin = tCin.getText(), pass = tPass.getText();
            runAsync(() -> Database.addEtudiant(nom, cin, pass));
        });
        
        bMod.addActionListener(e -> {
            int r = tableEtudiants.getSelectedRow();
            if(r>=0) {
                int id = Integer.parseInt(modelEtudiants.getValueAt(r,0).toString());
                String nom = tNom.getText(), cin = tCin.getText(), pass = tPass.getText();
                runAsync(() -> Database.updateEtudiant(id, nom, cin, pass));
            }
        });

        bDel.addActionListener(e -> {
            int r = tableEtudiants.getSelectedRow();
            if(r>=0) {
                String idStr = modelEtudiants.getValueAt(r,0).toString();
                try { Database.deleteEtudiant(Integer.parseInt(idStr)); refreshAll(); } catch(Exception ex){}
            }
        });

        p.add(new JScrollPane(tableEtudiants), BorderLayout.CENTER);
        p.add(f, BorderLayout.SOUTH);
        return p;
    }

    private JPanel createPanelEmprunts() {
        JPanel p = createContentPanel("Suivi des Emprunts");
        
        modelEmprunts = new DefaultTableModel(new String[]{"ID", "Livre", "Etudiant", "Date Emp", "Date Ret", "Statut"}, 0);
        tableEmprunts = createTable(modelEmprunts);
        
        // Custom Renderer for Status
        tableEmprunts.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String status = (String) value;
                if ("En cours".equals(status)) {
                    setForeground(new Color(234, 88, 12)); // Orange
                    setFont(getFont().deriveFont(Font.BOLD));
                } else if ("Rendu".equals(status)) {
                    setForeground(COL_SUCCESS);
                    setFont(getFont().deriveFont(Font.BOLD));
                } else {
                    setForeground(COL_TEXT);
                }
                return this;
            }
        });

        JPanel f = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        f.setBackground(COL_SURFACE);
        
        JButton bValider = new StyledButton("✅ Valider Retour", COL_SUCCESS, Color.WHITE);
        bValider.addActionListener(e -> {
            int r = tableEmprunts.getSelectedRow();
            if(r>=0) {
                String status = (String)modelEmprunts.getValueAt(r, 5);
                if("Rendu".equals(status)) return;
                
                int empId = Integer.parseInt((String)modelEmprunts.getValueAt(r,0));
                // Find livreId logic
                for(Emprunt em : Database.getEmpruntsActifs()) {
                    if(em.getId() == empId) {
                        int lid = em.getLivreId();
                        runAsync(() -> Database.validerRetour(empId, lid));
                        return;
                    }
                }
            }
        });
        f.add(bValider);

        p.add(new JScrollPane(tableEmprunts), BorderLayout.CENTER);
        p.add(f, BorderLayout.SOUTH);
        return p;
    }

    private JPanel createPanelReservations() {
        JPanel p = createContentPanel("File d'attente Réservations");
        
        modelReservations = new DefaultTableModel(new String[]{"ID", "Livre", "Etudiant", "Date"}, 0);
        tableReservations = createTable(modelReservations);
        
        JPanel f = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        f.setBackground(COL_SURFACE);
        JButton bDel = new StyledButton("🗑️ Supprimer", COL_DANGER, Color.WHITE);
        
        bDel.addActionListener(e -> {
            int r = tableReservations.getSelectedRow();
            if(r>=0) {
                int id = Integer.parseInt((String)modelReservations.getValueAt(r,0));
                runAsync(() -> Database.deleteReservation(id));
            }
        });
        f.add(bDel);

        p.add(new JScrollPane(tableReservations), BorderLayout.CENTER);
        p.add(f, BorderLayout.SOUTH);
        return p;
    }

    // --- UTILS & STYLING ---

    private JPanel createContentPanel(String title) {
        JPanel p = new JPanel(new BorderLayout(0, 20));
        p.setBackground(COL_BG);
        p.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        JLabel l = new JLabel(title);
        l.setFont(FONT_TITLE);
        l.setForeground(COL_TEXT);
        p.add(l, BorderLayout.NORTH);
        
        return p;
    }

    private JTable createTable(DefaultTableModel model) {
        JTable t = new JTable(model);
        t.setRowHeight(35);
        t.setFont(FONT_NORMAL);
        t.setShowVerticalLines(false);
        t.setGridColor(new Color(229, 231, 235));
        t.setSelectionBackground(new Color(224, 231, 255)); // Light indigo
        t.setSelectionForeground(COL_PRIMARY);
        
        JTableHeader th = t.getTableHeader();
        th.setFont(FONT_HEADER);
        th.setBackground(new Color(249, 250, 251)); // Gray 50
        th.setForeground(COL_TEXT_MUTED);
        th.setPreferredSize(new Dimension(0, 40));
        
        return t;
    }

    private void styleInput(JTextField tf) {
        tf.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        tf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(209, 213, 219)),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
    }
    
    private void startAutoRefresh() {
        refreshAll();
        if(refreshTimer == null) {
            refreshTimer = new Timer(5000, e -> refreshAll());
            refreshTimer.start();
        } else {
            refreshTimer.start();
        }
    }
    
    private void stopAutoRefresh() {
        if(refreshTimer != null) refreshTimer.stop();
    }

    private void refreshAll() {
        // Run in background to avoid freezing UI
        new SwingWorker<Object, Void>(){
            List<Livre> livres; List<String[]> etuds; List<String[]> emprunts; List<String[]> reservations;
            @Override protected Object doInBackground() throws Exception {
                livres = Database.getLivres();
                etuds = Database.getListeEtudiants();
                emprunts = Database.getHistoriqueEmprunts();
                reservations = Database.getReservations();
                return null;
            }
            @Override protected void done() {
                updateTable(modelLivres, livres);
                updateTableStrings(modelEtudiants, etuds);
                updateTableStrings(modelEmprunts, emprunts);
                updateTableStrings(modelReservations, reservations);
            }
        }.execute();
    }
    
    // Helpers to update table without losing selection if possible
    private void updateTable(DefaultTableModel model, List<Livre> data) {
        // Simple full refresh for now (preserving selection is harder without unique IDs mapping)
        model.setRowCount(0);
        for(Livre l : data) model.addRow(new Object[]{l.getId(), l.getTitre(), l.getAuteur(), l.getCategorie(), l.isDisponible()});
    }
    private void updateTableStrings(DefaultTableModel model, List<String[]> data) {
        model.setRowCount(0);
        for(String[] s : data) model.addRow(s);
    }

    private interface Action { void run() throws Exception; }
    private void runAsync(Action a) {
        new SwingWorker<Void, Void>(){
            @Override protected Void doInBackground() throws Exception { a.run(); return null; }
            @Override protected void done() { refreshAll(); }
        }.execute();
    }

    // --- CUSTOM COMPONENTS ---
    
    private static class StyledButton extends JButton {

        public StyledButton(String text, Color bg, Color fg) {
            super(text);
            setBackground(bg);
            setForeground(fg);
            setFont(new Font("Segoe UI", Font.BOLD, 13));
            setFocusPainted(false);
            setBorderPainted(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(8, 16, 8, 16));
        }
    }
}
