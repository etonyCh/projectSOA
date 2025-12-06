package com.biblio.model;

public class Emprunt {
    private int id;
    private int livreId;
    private int etudiantId;
    private String dateEmprunt;
    private boolean actif;
    private boolean pendingReturn;

    public Emprunt(int id, int livreId, int etudiantId, String dateEmprunt) {
        this(id, livreId, etudiantId, dateEmprunt, false);
    }

    public Emprunt(int id, int livreId, int etudiantId, String dateEmprunt, boolean pendingReturn) {
        this.id = id;
        this.livreId = livreId;
        this.etudiantId = etudiantId;
        this.dateEmprunt = dateEmprunt;
        this.actif = true;
        this.pendingReturn = pendingReturn;
    }

    public int getId() { return id; }
    public int getLivreId() { return livreId; }
    public int getEtudiantId() { return etudiantId; }
    public String getDateEmprunt() { return dateEmprunt; }
    public boolean isActif() { return actif; }
    public boolean isPendingReturn() { return pendingReturn; }
    public void setActif(boolean actif) { this.actif = actif; }
}