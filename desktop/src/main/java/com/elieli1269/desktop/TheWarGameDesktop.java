package com.elieli1269.desktop;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public final class TheWarGameDesktop extends JFrame {
    private static final int SAVE_VERSION = 1;
    private static final Color BACKGROUND = new Color(17, 31, 43);
    private static final Color PANEL = new Color(25, 45, 59);
    private static final Color PANEL_LIGHT = new Color(35, 59, 75);
    private static final Color TEXT = new Color(242, 240, 230);
    private static final Color MUTED = new Color(169, 188, 198);
    private static final Color FRANCE = new Color(52, 107, 192);
    private static final Color GOLD = new Color(255, 196, 92);
    private static final Color TARGET = new Color(255, 125, 91);
    private static final String[] MONTHS = {
        "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
        "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"
    };

    private final List<Region> regions = new ArrayList<>();
    private final Map<String, Region> byId = new HashMap<>();
    private final Map<String, Color> ownerColors = new HashMap<>();
    private final Preferences preferences = Preferences.userRoot().node("elieli1269/TheWarGame");
    private final Random random = new Random();

    private int month = 8;
    private int year = 1939;
    private int turn = 1;
    private int supplies = 130;
    private int manpower = 90;
    private String selectedId;
    private String targetId;
    private boolean gameOver;
    private String endMessage = "";

    private MapPanel mapPanel;
    private JLabel dateLabel;
    private JLabel turnLabel;
    private JLabel suppliesLabel;
    private JLabel manpowerLabel;
    private JLabel selectedNameLabel;
    private JLabel selectedOwnerLabel;
    private JLabel selectedUnitsLabel;
    private JLabel selectedDefenceLabel;
    private JLabel targetNameLabel;
    private JLabel targetOwnerLabel;
    private JTextArea eventLog;
    private JButton attackButton;
    private JButton infantryButton;
    private JButton tanksButton;
    private JButton rocketsButton;
    private JButton fortButton;
    private JButton generalButton;
    private JButton endMonthButton;

    private String log1 = "Bienvenue sur TheWarGame.";
    private String log2 = "Touchez une région française pour commencer.";
    private String log3 = "Les frontières affichées sont celles d'aujourd'hui.";

    private static final class Region {
        final String id;
        final String name;
        final String initialOwner;
        final String countryCode;
        final String city;
        final boolean capital;
        final double x;
        final double y;
        final double cityX;
        final double cityY;
        final List<String> links;
        final List<Path2D.Double> polygons = new ArrayList<>();

        String owner;
        int infantry;
        int tanks;
        int rockets;
        int fort;
        boolean general;

        Region(String id, String name, String owner, String countryCode, String city,
               boolean capital, double x, double y, double cityX, double cityY,
               List<String> links) {
            this.id = id;
            this.name = name;
            this.owner = owner;
            this.initialOwner = owner;
            this.countryCode = countryCode;
            this.city = city;
            this.capital = capital;
            this.x = x;
            this.y = y;
            this.cityX = cityX;
            this.cityY = cityY;
            this.links = links;
            this.infantry = capital ? 5 : 2;
            this.tanks = capital ? 1 : 0;
            this.rockets = 0;
            this.fort = capital ? 1 : 0;
            this.general = false;
        }

        int total() {
            return infantry + tanks + rockets;
        }

        double strength() {
            return infantry + tanks * 2.25 + rockets * 1.75 + fort * 2.0 + (general ? 3 : 0);
        }
    }

    private TheWarGameDesktop() throws Exception {
        super("TheWarGame — Stratégie européenne");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1120, 720));
        setSize(1510, 940);
        setLocationRelativeTo(null);
        loadMap();
        assignColors();
        loadSave();
        buildInterface();
        setDateText();
        refreshInterface();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                TheWarGameDesktop game = new TheWarGameDesktop();
                game.setVisible(true);
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(null,
                    "Impossible de charger la carte européenne.\n" + ex.getMessage(),
                    "Erreur TheWarGame", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void loadMap() throws Exception {
        try (InputStream in = TheWarGameDesktop.class.getResourceAsStream("/europe_regions.json")) {
            if (in == null) {
                throw new IllegalStateException("Le fichier de carte europe_regions.json est absent.");
            }
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> root = mapper.readValue(in, new TypeReference<Map<String, Object>>() {});
            Object regionData = root.get("regions");
            if (!(regionData instanceof List<?> entries)) {
                throw new IllegalStateException("Le fichier de carte ne contient aucune région.");
            }
            for (Object entry : entries) {
                if (!(entry instanceof Map<?, ?> raw)) continue;
                Map<String, Object> item = (Map<String, Object>) raw;
                List<?> center = asList(item.get("center"));
                List<?> cityPoint = asList(item.get("cityPoint"));
                String id = string(item.get("id"), "");
                String name = string(item.get("name"), "Région");
                String owner = string(item.get("owner"), "Autre");
                String cc = string(item.get("cc"), "");
                String city = string(item.get("city"), "");
                boolean capital = Boolean.TRUE.equals(item.get("capital"));
                double x = numberAt(center, 0, 0);
                double y = numberAt(center, 1, 0);
                double cityX = numberAt(cityPoint, 0, x);
                double cityY = numberAt(cityPoint, 1, y);
                List<String> links = new ArrayList<>();
                for (Object link : asList(item.get("links"))) links.add(String.valueOf(link));

                Region region = new Region(id, name, owner, cc, city, capital, x, y, cityX, cityY, links);
                List<?> polygonList = asList(item.get("polygons"));
                for (Object polygonObject : polygonList) {
                    List<?> points = asList(polygonObject);
                    if (points.size() < 4) continue;
                    Path2D.Double path = new Path2D.Double(Path2D.WIND_EVEN_ODD, points.size());
                    for (int i = 0; i < points.size(); i++) {
                        List<?> xy = asList(points.get(i));
                        double px = numberAt(xy, 0, 0);
                        double py = numberAt(xy, 1, 0);
                        if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
                    }
                    path.closePath();
                    region.polygons.add(path);
                }
                if (region.polygons.isEmpty()) continue;
                regions.add(region);
                byId.put(region.id, region);
            }
        }
        if (regions.size() < 100) {
            throw new IllegalStateException("La carte n'est pas complète (" + regions.size() + " régions).");
        }

        for (Region region : regions) {
            if (region.owner.equals("France") && ("Paris".equals(region.city) || region.capital)) {
                selectedId = region.id;
                if ("Paris".equals(region.city)) break;
            }
        }
        if (selectedId == null) {
            for (Region region : regions) {
                if ("France".equals(region.owner)) { selectedId = region.id; break; }
            }
        }
        if (selectedId == null && !regions.isEmpty()) selectedId = regions.get(0).id;
    }

    private static List<?> asList(Object value) {
        return value instanceof List<?> list ? list : Collections.emptyList();
    }

    private static double numberAt(List<?> values, int index, double fallback) {
        if (index >= values.size()) return fallback;
        Object value = values.get(index);
        return value instanceof Number n ? n.doubleValue() : fallback;
    }

    private static String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private void assignColors() {
        Set<String> owners = new TreeSet<>();
        for (Region region : regions) owners.add(region.initialOwner);
        ownerColors.put("France", FRANCE);
        ownerColors.put("Allemagne", new Color(119, 127, 137));
        ownerColors.put("Royaume-Uni", new Color(58, 139, 116));
        ownerColors.put("Russie", new Color(183, 71, 69));
        ownerColors.put("URSS", new Color(183, 71, 69));
        ownerColors.put("Espagne", new Color(210, 168, 79));
        ownerColors.put("Portugal", new Color(91, 161, 142));
        ownerColors.put("Italie", new Color(88, 151, 106));
        ownerColors.put("Pologne", new Color(203, 118, 87));
        ownerColors.put("Pays-Bas", new Color(229, 163, 79));
        ownerColors.put("Belgique", new Color(185, 160, 92));
        ownerColors.put("Suisse", new Color(182, 91, 85));
        ownerColors.put("Ukraine", new Color(183, 157, 77));

        int index = 0;
        for (String owner : owners) {
            if (ownerColors.containsKey(owner)) continue;
            float hue = (float) ((index * 0.618033988749895 + 0.09) % 1.0);
            Color candidate = Color.getHSBColor(hue, 0.48f, 0.88f);
            ownerColors.put(owner, candidate);
            index++;
        }
        ownerColors.put("France", FRANCE);
    }

    private Color colorFor(String owner) {
        return ownerColors.getOrDefault(owner, new Color(151, 154, 154));
    }

    private void buildInterface() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BACKGROUND);
        setContentPane(root);
        root.add(buildHeader(), BorderLayout.NORTH);

        mapPanel = new MapPanel();
        root.add(mapPanel, BorderLayout.CENTER);
        root.add(buildCommandPanel(), BorderLayout.EAST);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(20, 0));
        header.setBackground(BACKGROUND);
        header.setBorder(new EmptyBorder(14, 20, 14, 20));

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("THE WARGAME");
        title.setForeground(TEXT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 23));
        JLabel subtitle = new JLabel("CARTE EUROPÉENNE • FRONTIÈRES ACTUELLES • CONQUÊTE");
        subtitle.setForeground(MUTED);
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        titles.add(title);
        titles.add(Box.createVerticalStrut(4));
        titles.add(subtitle);

        JPanel stats = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        stats.setOpaque(false);
        turnLabel = statLabel();
        dateLabel = statLabel();
        suppliesLabel = statLabel();
        manpowerLabel = statLabel();
        stats.add(chip("TOUR", turnLabel));
        stats.add(chip("MOIS", dateLabel));
        stats.add(chip("RAVITAILLEMENT", suppliesLabel));
        stats.add(chip("EFFECTIFS", manpowerLabel));

        header.add(titles, BorderLayout.WEST);
        header.add(stats, BorderLayout.EAST);
        return header;
    }

    private JLabel statLabel() {
        JLabel label = new JLabel("—");
        label.setForeground(new Color(255, 213, 141));
        label.setFont(new Font("Segoe UI", Font.BOLD, 14));
        return label;
    }

    private JPanel chip(String caption, JLabel value) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(PANEL_LIGHT);
        panel.setBorder(new EmptyBorder(7, 13, 7, 13));
        JLabel small = new JLabel(caption);
        small.setForeground(MUTED);
        small.setFont(new Font("Segoe UI", Font.BOLD, 9));
        panel.add(small);
        panel.add(Box.createVerticalStrut(4));
        panel.add(value);
        return panel;
    }

    private JPanel buildCommandPanel() {
        JPanel side = new JPanel();
        side.setBackground(PANEL);
        side.setPreferredSize(new Dimension(340, 100));
        side.setBorder(new EmptyBorder(12, 16, 14, 16));
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));

        JLabel heading = titleLabel("COMMANDEMENT");
        heading.setFont(new Font("Segoe UI", Font.BOLD, 17));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(heading);
        side.add(Box.createVerticalStrut(7));

        JLabel player = new JLabel("FRANCE • JOUEUR");
        player.setForeground(new Color(128, 182, 250));
        player.setFont(new Font("Segoe UI", Font.BOLD, 11));
        player.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(player);
        side.add(Box.createVerticalStrut(12));

        side.add(sectionLabel("RÉGION SÉLECTIONNÉE"));
        selectedNameLabel = titleLabel("—");
        selectedNameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        selectedNameLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        side.add(selectedNameLabel);

        selectedOwnerLabel = bodyLabel("Contrôle : —");
        side.add(selectedOwnerLabel);
        selectedUnitsLabel = bodyLabel("Unités : —");
        side.add(selectedUnitsLabel);
        selectedDefenceLabel = bodyLabel("Défense : —");
        side.add(selectedDefenceLabel);
        side.add(Box.createVerticalStrut(10));

        side.add(sectionLabel("CIBLE"));
        JPanel targetCard = new JPanel();
        targetCard.setLayout(new BoxLayout(targetCard, BoxLayout.Y_AXIS));
        targetCard.setBackground(new Color(20, 38, 50));
        targetCard.setBorder(new EmptyBorder(10, 11, 10, 11));
        targetCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        targetCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 78));
        targetNameLabel = titleLabel("Aucune cible choisie");
        targetNameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        targetOwnerLabel = bodyLabel("Touchez un voisin ennemi sur la carte.");
        targetCard.add(targetNameLabel);
        targetCard.add(Box.createVerticalStrut(4));
        targetCard.add(targetOwnerLabel);
        side.add(targetCard);
        side.add(Box.createVerticalStrut(11));

        attackButton = actionButton("⚔  ATTAQUER", new Color(151, 75, 66));
        attackButton.setToolTipText("Conquérir la région ennemie choisie.");
        attackButton.addActionListener(e -> attack());
        side.add(fillWidth(attackButton));
        side.add(Box.createVerticalStrut(8));

        JPanel recruitRow1 = new JPanel(new GridLayout(1, 2, 7, 0));
        recruitRow1.setOpaque(false);
        infantryButton = actionButton("+ Infanterie", new Color(44, 92, 128));
        infantryButton.addActionListener(e -> recruit("infantry"));
        tanksButton = actionButton("+ Blindés", new Color(114, 90, 53));
        tanksButton.addActionListener(e -> recruit("tanks"));
        recruitRow1.add(infantryButton);
        recruitRow1.add(tanksButton);
        recruitRow1.setAlignmentX(Component.LEFT_ALIGNMENT);
        recruitRow1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        side.add(recruitRow1);
        side.add(Box.createVerticalStrut(7));

        JPanel recruitRow2 = new JPanel(new GridLayout(1, 2, 7, 0));
        recruitRow2.setOpaque(false);
        rocketsButton = actionButton("+ Artillerie", new Color(96, 72, 120));
        rocketsButton.addActionListener(e -> recruit("rockets"));
        fortButton = actionButton("Fortifier", new Color(66, 108, 89));
        fortButton.addActionListener(e -> fortify());
        recruitRow2.add(rocketsButton);
        recruitRow2.add(fortButton);
        recruitRow2.setAlignmentX(Component.LEFT_ALIGNMENT);
        recruitRow2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        side.add(recruitRow2);
        side.add(Box.createVerticalStrut(7));

        generalButton = actionButton("Affecter un général", new Color(119, 100, 47));
        generalButton.addActionListener(e -> assignGeneral());
        side.add(fillWidth(generalButton));
        side.add(Box.createVerticalStrut(12));

        side.add(sectionLabel("DERNIERS ÉVÉNEMENTS"));
        eventLog = new JTextArea(4, 20);
        eventLog.setEditable(false);
        eventLog.setLineWrap(true);
        eventLog.setWrapStyleWord(true);
        eventLog.setForeground(TEXT);
        eventLog.setBackground(new Color(20, 38, 50));
        eventLog.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        eventLog.setBorder(new EmptyBorder(8, 8, 8, 8));
        JScrollPane logScroll = new JScrollPane(eventLog);
        logScroll.setBorder(BorderFactory.createEmptyBorder());
        logScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        logScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 104));
        side.add(logScroll);
        side.add(Box.createVerticalStrut(10));

        endMonthButton = actionButton("TERMINER LE MOIS  ▶", new Color(156, 113, 57));
        endMonthButton.addActionListener(e -> nextMonth());
        side.add(fillWidth(endMonthButton));
        side.add(Box.createVerticalStrut(6));

        JButton newGame = actionButton("Nouvelle partie", new Color(52, 70, 82));
        newGame.addActionListener(e -> newGame());
        side.add(fillWidth(newGame));
        side.add(Box.createVerticalStrut(7));

        JLabel instructions = new JLabel("<html><div style='width:285px'>Sélectionnez une région française, puis un voisin surligné en jaune. Appuyez sur ATTAQUER pour tenter la conquête.</div></html>");
        instructions.setForeground(MUTED);
        instructions.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        instructions.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(instructions);
        side.add(Box.createVerticalGlue());
        return side;
    }

    private JLabel titleLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT);
        label.setFont(new Font("Segoe UI", Font.BOLD, 13));
        return label;
    }

    private JLabel bodyLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(3, 0, 2, 0));
        return label;
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(157, 178, 189));
        label.setFont(new Font("Segoe UI", Font.BOLD, 9));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(3, 0, 5, 0));
        return label;
    }

    private JPanel fillWidth(JComponent component) {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        wrap.add(component, BorderLayout.CENTER);
        return wrap;
    }

    private JButton actionButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setForeground(Color.WHITE);
        button.setBackground(color);
        button.setOpaque(true);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(color.brighter(), 1),
            new EmptyBorder(7, 9, 7, 9)));
        button.setFont(new Font("Segoe UI", Font.BOLD, 11));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private void setDateText() {
        if (turnLabel != null) turnLabel.setText(String.valueOf(turn));
        if (dateLabel != null) dateLabel.setText(MONTHS[month] + " " + year);
        if (suppliesLabel != null) suppliesLabel.setText(String.valueOf(supplies));
        if (manpowerLabel != null) manpowerLabel.setText(String.valueOf(manpower));
    }

    private Region selected() {
        return selectedId == null ? null : byId.get(selectedId);
    }

    private Region target() {
        return targetId == null ? null : byId.get(targetId);
    }

    private boolean isPlayerRegion(Region region) {
        return region != null && "France".equals(region.owner);
    }

    private boolean adjacent(Region a, Region b) {
        return a != null && b != null && a.links.contains(b.id);
    }

    private void refreshInterface() {
        Region source = selected();
        Region destination = target();

        if (source != null) {
            selectedNameLabel.setText(clip(source.name, 28));
            selectedOwnerLabel.setText("Contrôle : " + source.owner);
            selectedOwnerLabel.setForeground(colorFor(source.owner));
            selectedUnitsLabel.setText("Infanterie " + source.infantry
                + "   Blindés " + source.tanks + "   Artillerie " + source.rockets);
            selectedDefenceLabel.setText("Fortifications " + source.fort
                + "   Général " + (source.general ? "oui" : "non"));
        } else {
            selectedNameLabel.setText("Aucune région");
            selectedOwnerLabel.setText("—");
            selectedUnitsLabel.setText("—");
            selectedDefenceLabel.setText("—");
        }

        if (destination != null) {
            targetNameLabel.setText(clip(destination.name, 29));
            targetOwnerLabel.setText(destination.owner + " • " + destination.total()
                + " unités • force " + Math.round(destination.strength()));
        } else {
            targetNameLabel.setText("Aucune cible choisie");
            targetOwnerLabel.setText("Touchez un voisin ennemi sur la carte.");
        }

        boolean canOperate = !gameOver && isPlayerRegion(source);
        attackButton.setEnabled(canOperate && destination != null
            && !"France".equals(destination.owner) && adjacent(source, destination)
            && source.total() > 0);
        if (destination != null) {
            attackButton.setText("⚔  ATTAQUER : " + clip(destination.name, 20));
        } else {
            attackButton.setText("⚔  ATTAQUER");
        }
        infantryButton.setEnabled(canOperate && supplies >= 16 && manpower >= 8);
        tanksButton.setEnabled(canOperate && supplies >= 30 && manpower >= 12);
        rocketsButton.setEnabled(canOperate && supplies >= 24 && manpower >= 10);
        fortButton.setEnabled(canOperate && supplies >= 28 && source.fort < 5);
        generalButton.setEnabled(canOperate && !source.general && supplies >= 35);
        endMonthButton.setEnabled(!gameOver);

        eventLog.setText(log1 + "\n" + log2 + "\n" + log3);
        setDateText();
        if (mapPanel != null) mapPanel.repaint();
    }

    private void attack() {
        Region source = selected();
        Region destination = target();
        if (gameOver || !isPlayerRegion(source) || destination == null
            || "France".equals(destination.owner) || !adjacent(source, destination)) {
            setLog("Cible invalide.",
                "Choisissez une région française puis un voisin ennemi surligné.",
                "Le bouton ATTAQUER s'active uniquement avec une cible valide.");
            refreshInterface();
            return;
        }
        if (source.total() <= 0) {
            setLog("Cette région n'a plus d'unités.",
                "Recrutez des troupes ou sélectionnez une autre région.",
                "La conquête ne peut commencer qu'avec des unités disponibles.");
            refreshInterface();
            return;
        }

        String destinationName = destination.name;
        boolean won = battle(source, destination);
        if (won) {
            destination.owner = "France";
            transferOccupationForce(source, destination);
            destination.fort = Math.max(0, destination.fort - 1);
            destination.general = false;
            setLog("VICTOIRE : " + destinationName + " est conquise !",
                "La région est maintenant française.",
                "Continuez vers les régions ennemies voisines.");
            if (countOwned("France") >= Math.ceil(regions.size() * 0.5)) {
                gameOver = true;
                endMessage = "Victoire : la France contrôle la majorité de l'Europe.";
                setLog("VICTOIRE !", endMessage, "Démarrez une nouvelle partie pour rejouer.");
            }
        } else {
            setLog("Attaque repoussée à " + destinationName + ".",
                "Les effectifs ont été réduits durant le combat.",
                "Renforcez cette région ou essayez depuis une autre région.");
        }
        targetId = null;
        save();
        refreshInterface();
    }

    private boolean battle(Region attacker, Region defender) {
        if (attacker.total() <= 0) return false;
        double attackPower = (attacker.infantry + attacker.tanks * 2.25
            + attacker.rockets * 1.75 + (attacker.general ? 3 : 0))
            * (0.88 + random.nextDouble() * 0.28);
        double defencePower = (defender.infantry + defender.tanks * 2.2
            + defender.rockets * 1.7 + defender.fort * 2.0
            + (defender.general ? 3 : 0))
            * (0.90 + random.nextDouble() * 0.25);
        boolean attackerWins = attackPower > defencePower;

        int wantedAttackLosses = defender.total() == 0 ? 0
            : Math.max(1, (int) Math.round(defencePower / Math.max(1.0, attackPower) * 1.8));
        int maximumAttackLosses = Math.max(0, attacker.total() - (attackerWins ? 1 : 0));
        removeUnits(attacker, Math.min(maximumAttackLosses, wantedAttackLosses));

        if (defender.total() > 0) {
            int defenderLosses = Math.min(defender.total(),
                Math.max(1, (int) Math.round(attackPower / Math.max(1.0, defencePower) * 1.8)));
            removeUnits(defender, defenderLosses);
        }
        return attackerWins && attacker.total() > 0;
    }

    private void removeUnits(Region region, int amount) {
        int remaining = amount;
        while (remaining > 0 && region.total() > 0) {
            if (region.infantry > 0) region.infantry--;
            else if (region.tanks > 0) region.tanks--;
            else if (region.rockets > 0) region.rockets--;
            remaining--;
        }
    }

    private void transferOccupationForce(Region from, Region to) {
        int inf = from.infantry / 2;
        int tanks = from.tanks / 2;
        int rockets = from.rockets / 2;
        if (inf + tanks + rockets == 0) {
            if (from.infantry > 0) inf = 1;
            else if (from.tanks > 0) tanks = 1;
            else if (from.rockets > 0) rockets = 1;
        }
        from.infantry -= inf;
        from.tanks -= tanks;
        from.rockets -= rockets;
        to.infantry = inf;
        to.tanks = tanks;
        to.rockets = rockets;
    }

    private void recruit(String type) {
        Region source = selected();
        if (!isPlayerRegion(source) || gameOver) return;
        if ("infantry".equals(type)) {
            if (supplies < 16 || manpower < 8) {
                setLog("Ressources insuffisantes.", "Il faut 16 de ravitaillement et 8 effectifs.", log3);
                refreshInterface(); return;
            }
            supplies -= 16; manpower -= 8; source.infantry += 2;
            setLog("Deux unités d'infanterie recrutées.", source.name, log3);
        } else if ("tanks".equals(type)) {
            if (supplies < 30 || manpower < 12) {
                setLog("Pas assez de matériel.", "Il faut 30 de ravitaillement et 12 effectifs.", log3);
                refreshInterface(); return;
            }
            supplies -= 30; manpower -= 12; source.tanks++;
            setLog("Un bataillon blindé rejoint " + source.name + ".", "Les effectifs sont prêts.", log3);
        } else {
            if (supplies < 24 || manpower < 10) {
                setLog("Ressources insuffisantes.", "Il faut 24 de ravitaillement et 10 effectifs.", log3);
                refreshInterface(); return;
            }
            supplies -= 24; manpower -= 10; source.rockets++;
            setLog("Une unité d'artillerie rejoint " + source.name + ".", "Les effectifs sont prêts.", log3);
        }
        save();
        refreshInterface();
    }

    private void fortify() {
        Region source = selected();
        if (!isPlayerRegion(source) || gameOver) return;
        if (supplies < 28 || source.fort >= 5) {
            setLog("Fortification impossible.", "Il faut 28 de ravitaillement ; maximum 5 niveaux.", log3);
            refreshInterface(); return;
        }
        supplies -= 28;
        source.fort++;
        setLog("Fortifications renforcées.", source.name, log3);
        save();
        refreshInterface();
    }

    private void assignGeneral() {
        Region source = selected();
        if (!isPlayerRegion(source) || gameOver) return;
        if (source.general || supplies < 35) {
            setLog("Affectation impossible.", "Il faut 35 de ravitaillement et aucun général déjà assigné.", log3);
            refreshInterface(); return;
        }
        supplies -= 35;
        source.general = true;
        setLog("Un général est affecté à " + source.name + ".", "Le commandement augmente la force de combat.", log3);
        save();
        refreshInterface();
    }

    private void nextMonth() {
        if (gameOver) return;
        month++;
        if (month >= 12) { month = 0; year++; }
        turn++;
        supplies += 22;
        manpower += 14;

        String battleNote = "Les pays se réorganisent.";
        int battles = 0;
        int attempts = 0;
        int limit = Math.min(45, Math.max(12, regions.size() / 40));
        List<Region> order = new ArrayList<>(regions);
        Collections.shuffle(order, random);

        for (Region source : order) {
            if (attempts >= limit) break;
            if ("France".equals(source.owner) || source.total() <= 0) continue;
            List<Region> options = new ArrayList<>();
            for (String id : source.links) {
                Region neighbour = byId.get(id);
                if (neighbour != null && !source.owner.equals(neighbour.owner)) options.add(neighbour);
            }
            if (options.isEmpty() || random.nextDouble() > 0.24) continue;
            attempts++;
            Region destination = options.get(random.nextInt(options.size()));
            String oldOwner = destination.owner;
            if (battle(source, destination)) {
                destination.owner = source.owner;
                transferOccupationForce(source, destination);
                destination.fort = Math.max(0, destination.fort - 1);
                destination.general = false;
                battleNote = "Le front change près de " + destination.name + ".";
                if ("France".equals(oldOwner)) battleNote = "ALERTE : " + destination.name + " est attaquée !";
                battles++;
            }
        }
        if (battles == 0) battleNote = "Aucune conquête ce mois-ci. Les armées se réorganisent.";
        setLog(MONTHS[month] + " " + year + " : ravitaillement reçu.", battleNote,
            "Les pays contrôlés par l'ordinateur ont joué leur tour.");
        int franceRegions = countOwned("France");
        if (franceRegions == 0) {
            gameOver = true;
            endMessage = "La France a perdu toutes ses régions.";
            setLog("DÉFAITE", endMessage, "Démarrez une nouvelle partie pour rejouer.");
        } else if (franceRegions >= Math.ceil(regions.size() * 0.5)) {
            gameOver = true;
            endMessage = "La France contrôle la majorité de l'Europe.";
            setLog("VICTOIRE !", endMessage, "Démarrez une nouvelle partie pour rejouer.");
        }
        save();
        refreshInterface();
    }

    private int countOwned(String owner) {
        int count = 0;
        for (Region region : regions) if (owner.equals(region.owner)) count++;
        return count;
    }

    private void newGame() {
        int option = JOptionPane.showConfirmDialog(this,
            "Recommencer la campagne et effacer la sauvegarde actuelle ?",
            "Nouvelle partie", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (option != JOptionPane.YES_OPTION) return;
        for (Region region : regions) {
            region.owner = region.initialOwner;
            region.infantry = region.capital ? 5 : 2;
            region.tanks = region.capital ? 1 : 0;
            region.rockets = 0;
            region.fort = region.capital ? 1 : 0;
            region.general = false;
        }
        month = 8; year = 1939; turn = 1;
        supplies = 130; manpower = 90;
        gameOver = false; endMessage = "";
        targetId = null;
        selectedId = regions.stream().filter(r -> "France".equals(r.owner) && "Paris".equals(r.city))
            .map(r -> r.id).findFirst().orElseGet(() -> regions.stream()
                .filter(r -> "France".equals(r.owner)).map(r -> r.id).findFirst().orElse(null));
        setLog("Nouvelle campagne commencée.", "Touchez une région française.", "Les frontières restent celles d'aujourd'hui.");
        save();
        refreshInterface();
    }

    private void setLog(String a, String b, String c) {
        log1 = a; log2 = b; log3 = c;
    }

    private void loadSave() {
        if (preferences.getInt("save_version", 0) != SAVE_VERSION) {
            try { preferences.clear(); } catch (BackingStoreException ignored) {}
            preferences.putInt("save_version", SAVE_VERSION);
            save();
            return;
        }
        month = preferences.getInt("month", 8);
        year = preferences.getInt("year", 1939);
        turn = preferences.getInt("turn", 1);
        supplies = preferences.getInt("supplies", 130);
        manpower = preferences.getInt("manpower", 90);
        gameOver = preferences.getBoolean("game_over", false);
        endMessage = preferences.get("end_message", "");
        for (Region region : regions) {
            String saved = preferences.get("region." + region.id, null);
            if (saved == null) continue;
            String[] pieces = saved.split("\\|", -1);
            try {
                if (pieces.length < 7) continue;
                region.owner = pieces[0];
                region.infantry = Math.max(0, Integer.parseInt(pieces[1]));
                region.tanks = Math.max(0, Integer.parseInt(pieces[2]));
                region.rockets = Math.max(0, Integer.parseInt(pieces[3]));
                region.fort = Math.max(0, Math.min(5, Integer.parseInt(pieces[4])));
                region.general = Boolean.parseBoolean(pieces[5]);
            } catch (RuntimeException ignored) {
                // Skip a damaged region record rather than preventing the game from opening.
            }
        }
        String savedSelected = preferences.get("selected_region", selectedId);
        if (savedSelected != null && byId.containsKey(savedSelected)) selectedId = savedSelected;
    }

    private void save() {
        preferences.putInt("save_version", SAVE_VERSION);
        preferences.putInt("month", month);
        preferences.putInt("year", year);
        preferences.putInt("turn", turn);
        preferences.putInt("supplies", supplies);
        preferences.putInt("manpower", manpower);
        preferences.putBoolean("game_over", gameOver);
        preferences.put("end_message", endMessage == null ? "" : endMessage);
        if (selectedId != null) preferences.put("selected_region", selectedId);
        for (Region region : regions) {
            preferences.put("region." + region.id, region.owner + "|" + region.infantry + "|"
                + region.tanks + "|" + region.rockets + "|" + region.fort + "|" + region.general + "|0");
        }
        try {
            preferences.flush();
        } catch (BackingStoreException ex) {
            setLog("Sauvegarde non disponible.", ex.getMessage(), log3);
        }
    }

    private static String clip(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, Math.max(1, max - 1)) + "…" : value;
    }

    private final class MapPanel extends JPanel {
        private double scale = 1;
        private double offsetX;
        private double offsetY;

        MapPanel() {
            setBackground(new Color(25, 49, 65));
            setPreferredSize(new Dimension(850, 700));
            setToolTipText("Carte de l'Europe : cliquez sur une région.");
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getButton() != MouseEvent.BUTTON1) return;
                    Region clicked = regionAt(e.getPoint());
                    if (clicked == null) {
                        setLog("Aucune région détectée.", "Touchez à l'intérieur d'une région.", log3);
                        refreshInterface();
                        return;
                    }
                    Region source = selected();
                    if ("France".equals(clicked.owner)) {
                        selectedId = clicked.id;
                        targetId = null;
                        setLog("Région française : " + clicked.name,
                            "Les voisins ennemis surlignés en jaune sont attaquables.",
                            "Choisissez une cible puis appuyez sur ATTAQUER.");
                    } else if (isPlayerRegion(source) && adjacent(source, clicked)) {
                        targetId = clicked.id;
                        setLog("Cible choisie : " + clicked.name,
                            "Contrôle actuel : " + clicked.owner + ".",
                            "Le bouton ATTAQUER lance la conquête.");
                    } else {
                        targetId = null;
                        setLog("Cette région n'est pas une cible valide.",
                            "Choisissez une région ennemie voisine de " + (source == null ? "votre région" : source.name) + ".",
                            "Les cibles légales sont surlignées en jaune.");
                    }
                    save();
                    refreshInterface();
                }
            });
        }

        private AffineTransform mapTransform() {
            scale = Math.min((getWidth() - 20.0) / 870.0, (getHeight() - 20.0) / 720.0);
            scale = Math.max(0.1, scale);
            offsetX = (getWidth() - 870.0 * scale) / 2.0;
            offsetY = (getHeight() - 720.0 * scale) / 2.0;
            AffineTransform transform = new AffineTransform();
            transform.translate(offsetX, offsetY);
            transform.scale(scale, scale);
            return transform;
        }

        private Region regionAt(Point screenPoint) {
            AffineTransform tx = mapTransform();
            Point2D world;
            try {
                world = tx.inverseTransform(screenPoint, null);
            } catch (Exception ex) {
                return null;
            }
            double wx = world.getX();
            double wy = world.getY();
            for (int i = regions.size() - 1; i >= 0; i--) {
                Region region = regions.get(i);
                for (Path2D.Double polygon : region.polygons) {
                    if (polygon.contains(wx, wy)) return region;
                }
            }
            Region nearest = null;
            double nearestPixels = Double.MAX_VALUE;
            for (Region region : regions) {
                double dx = (region.x - wx) * scale;
                double dy = (region.y - wy) * scale;
                double distance = dx * dx + dy * dy;
                if (distance < nearestPixels) {
                    nearestPixels = distance;
                    nearest = region;
                }
            }
            return nearestPixels <= 18 * 18 ? nearest : null;
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setColor(new Color(25, 49, 65));
                g.fillRect(0, 0, getWidth(), getHeight());

                AffineTransform tx = mapTransform();
                g.transform(tx);
                g.setFont(new Font("Segoe UI", Font.PLAIN, 9));
                g.setColor(new Color(112, 155, 175));
                g.drawString("OCÉAN ATLANTIQUE", 25, 435);
                g.drawString("MER DU NORD", 390, 210);
                g.drawString("MER BALTIQUE", 534, 170);
                g.drawString("MÉDITERRANÉE", 375, 668);

                Region source = selected();
                Region destination = target();
                for (Region region : regions) {
                    g.setColor(colorFor(region.owner));
                    for (Path2D.Double polygon : region.polygons) g.fill(polygon);

                    g.setStroke(new BasicStroke((float) (0.8 / scale)));
                    g.setColor(new Color(221, 226, 218));
                    for (Path2D.Double polygon : region.polygons) g.draw(polygon);

                    if (isPlayerRegion(source) && !isPlayerRegion(region) && adjacent(source, region)) {
                        g.setStroke(new BasicStroke((float) (2.0 / scale)));
                        g.setColor(GOLD);
                        for (Path2D.Double polygon : region.polygons) g.draw(polygon);
                    }
                    if (region == destination) {
                        g.setStroke(new BasicStroke((float) (3.0 / scale)));
                        g.setColor(TARGET);
                        for (Path2D.Double polygon : region.polygons) g.draw(polygon);
                    }
                    if (region == source) {
                        g.setStroke(new BasicStroke((float) (3.0 / scale)));
                        g.setColor(new Color(255, 238, 169));
                        for (Path2D.Double polygon : region.polygons) g.draw(polygon);
                    }

                    if (region.capital && !region.city.isEmpty()) {
                        g.setColor(new Color(23, 35, 42));
                        g.fillOval((int) (region.cityX - 3), (int) (region.cityY - 3), 6, 6);
                        g.setColor(new Color(255, 231, 179));
                        g.fillOval((int) (region.cityX - 1.5), (int) (region.cityY - 1.5), 3, 3);
                        g.setColor(new Color(255, 243, 215));
                        g.drawString(region.city, (float) region.cityX + 4, (float) region.cityY - 4);
                    }
                }

                g.setColor(new Color(255, 231, 160));
                g.setFont(new Font("Segoe UI", Font.BOLD, 11));
                if (source != null) {
                    g.drawString(clip(source.name, 35), (float) source.x + 7, (float) source.y + 14);
                }
                if (destination != null) {
                    g.setColor(TARGET);
                    g.drawString(clip(destination.name, 35), (float) destination.x + 7, (float) destination.y + 27);
                }
                g.setTransform(new AffineTransform());
                g.setColor(new Color(220, 228, 231));
                g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                g.drawString("Régions en jaune : cibles ennemies voisines", 14, getHeight() - 12);
            } finally {
                g.dispose();
            }
        }
    }
}
