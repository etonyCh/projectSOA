package com.biblio.model;

public class Livre {
    private int id;
    private String titre;
    private String auteur;
    private String categorie;
    private boolean disponible;

    public Livre() {}

    public Livre(int id, String titre, String auteur, String categorie, boolean disponible) {
        this.id = id;
        this.titre = titre;
        this.auteur = auteur;
        this.categorie = categorie;
        this.disponible = disponible;
    }

    // Getters et Setters obligatoires
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }
    public String getAuteur() { return auteur; }
    public void setAuteur(String auteur) { this.auteur = auteur; }
    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }
    public boolean isDisponible() { return disponible; }
    public void setDisponible(boolean disponible) { this.disponible = disponible; }
}