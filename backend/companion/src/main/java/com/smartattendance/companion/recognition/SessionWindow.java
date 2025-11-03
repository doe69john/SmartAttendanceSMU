package com.smartattendance.companion.recognition;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.DefaultListModel;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.OverlayLayout;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import java.util.function.Consumer;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;

/**
 * Swing window showing the live camera feed with overlays and a rolling event log.
 */
public final class SessionWindow implements AutoCloseable {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final AtomicReference<Collection<TrackedFace>> trackedFaces = new AtomicReference<>(List.of());
    private final DefaultListModel<String> logModel = new DefaultListModel<>();
    private final Webcam webcam;
    private final JFrame frame;
    private final WebcamPanel webcamPanel;
    private final List<RosterEntry> rosterEntries = new ArrayList<>();
    private final Set<String> rosterSubmitting = new HashSet<>();
    private JPanel rosterListPanel;
    private JTextField rosterSearchField;
    private String rosterFilter = "";
    private Consumer<RosterEntry> manualMarkListener;
    private Runnable endSessionListener;
    private JButton endSessionButton;

    public SessionWindow(Webcam webcam) {
        this.webcam = Objects.requireNonNull(webcam, "webcam");
        this.frame = new JFrame("SmartAttendance Companion");
        this.webcamPanel = new WebcamPanel(webcam, true);
        this.webcamPanel.setFillArea(true);
        this.webcamPanel.setPreferredSize(new Dimension(960, 540));
        this.webcamPanel.setFPSDisplayed(true);
        this.webcamPanel.setLayout(new BorderLayout());
        this.webcamPanel.setPainter(new OverlayPainter());
    }

    public void open() {
        SwingUtilities.invokeLater(() -> {
            frame.setLayout(new BorderLayout());

            JPanel videoContainer = new JPanel();
            videoContainer.setLayout(new OverlayLayout(videoContainer));
            webcamPanel.setAlignmentX(0f);
            webcamPanel.setAlignmentY(0f);
            videoContainer.add(webcamPanel);

            JPanel overlay = new JPanel(new BorderLayout());
            overlay.setOpaque(false);
            JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
            buttonRow.setOpaque(false);
            endSessionButton = new JButton("End Session");
            endSessionButton.addActionListener(e -> {
                if (endSessionListener != null) {
                    endSessionListener.run();
                }
            });
            buttonRow.add(endSessionButton);
            overlay.add(buttonRow, BorderLayout.NORTH);
            overlay.setAlignmentX(0f);
            overlay.setAlignmentY(0f);
            videoContainer.add(overlay);

            frame.add(videoContainer, BorderLayout.CENTER);

            JList<String> logList = new JList<>(logModel);
            logList.setForeground(Color.WHITE);
            logList.setBackground(new Color(20, 24, 31));
            logList.setSelectionBackground(new Color(45, 90, 45));
            logList.setFixedCellHeight(20);

            JScrollPane logScroll = new JScrollPane(logList);
            logScroll.setBorder(BorderFactory.createEmptyBorder());

            JPanel logPanel = new JPanel(new BorderLayout());
            logPanel.setBorder(new EmptyBorder(8, 8, 8, 4));
            logPanel.setOpaque(false);
            JLabel logLabel = new JLabel("Session log");
            logLabel.setFont(logLabel.getFont().deriveFont(Font.BOLD));
            logPanel.add(logLabel, BorderLayout.NORTH);
            logPanel.add(logScroll, BorderLayout.CENTER);
            logPanel.setPreferredSize(new Dimension(360, 220));

            rosterListPanel = new JPanel();
            rosterListPanel.setLayout(new BoxLayout(rosterListPanel, BoxLayout.Y_AXIS));
            rosterListPanel.setOpaque(false);

            JScrollPane rosterScroll = new JScrollPane(rosterListPanel);
            rosterScroll.setBorder(BorderFactory.createEmptyBorder());

            JPanel rosterHeader = new JPanel();
            rosterHeader.setLayout(new BorderLayout(0, 6));
            rosterHeader.setOpaque(false);
            JLabel rosterLabel = new JLabel("Session roster");
            rosterLabel.setFont(rosterLabel.getFont().deriveFont(Font.BOLD));
            rosterHeader.add(rosterLabel, BorderLayout.NORTH);
            rosterSearchField = new JTextField();
            rosterSearchField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    handleRosterFilterChange();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    handleRosterFilterChange();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    handleRosterFilterChange();
                }
            });
            rosterHeader.add(rosterSearchField, BorderLayout.SOUTH);

            JPanel rosterPanel = new JPanel(new BorderLayout());
            rosterPanel.setBorder(new EmptyBorder(4, 8, 8, 8));
            rosterPanel.setOpaque(false);
            rosterPanel.add(rosterHeader, BorderLayout.NORTH);
            rosterPanel.add(rosterScroll, BorderLayout.CENTER);

            JPanel sidePanel = new JPanel(new BorderLayout());
            sidePanel.setPreferredSize(new Dimension(360, 540));
            sidePanel.setOpaque(false);
            sidePanel.add(logPanel, BorderLayout.NORTH);
            sidePanel.add(rosterPanel, BorderLayout.CENTER);

            frame.add(sidePanel, BorderLayout.EAST);

            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    public void setManualMarkListener(Consumer<RosterEntry> manualMarkListener) {
        this.manualMarkListener = manualMarkListener;
    }

    public void setEndSessionListener(Runnable endSessionListener) {
        this.endSessionListener = endSessionListener;
    }

    public void setEndSessionEnabled(boolean enabled) {
        SwingUtilities.invokeLater(() -> {
            if (endSessionButton != null) {
                endSessionButton.setEnabled(enabled);
            }
        });
    }

    public void setRosterSubmissionInProgress(String studentId, boolean inProgress) {
        if (studentId == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (inProgress) {
                rosterSubmitting.add(studentId);
            } else {
                rosterSubmitting.remove(studentId);
            }
            refreshRosterList();
        });
    }

    public void updateRoster(List<RosterEntry> entries) {
        SwingUtilities.invokeLater(() -> {
            rosterEntries.clear();
            if (entries != null) {
                rosterEntries.addAll(entries);
            }
            Set<String> validIds = new HashSet<>();
            for (RosterEntry entry : rosterEntries) {
                if (entry.studentId() != null) {
                    validIds.add(entry.studentId());
                }
            }
            rosterSubmitting.retainAll(validIds);
            refreshRosterList();
        });
    }

    public void updateTrackedFaces(Collection<TrackedFace> faces) {
        trackedFaces.set(faces != null ? List.copyOf(faces) : List.of());
        webcamPanel.repaint();
    }

    public void appendEvent(RecognitionEvent event) {
        if (event == null) {
            return;
        }
        String friendlyMessage = toFriendlyMessage(event);
        if (friendlyMessage == null || friendlyMessage.isBlank()) {
            return;
        }
        final String logLine = "[" + TIME_FORMAT.format(event.getTimestamp()) + "] " + friendlyMessage;
        SwingUtilities.invokeLater(() -> {
            logModel.addElement(logLine);
            int size = logModel.getSize();
            if (size > 500) {
                logModel.removeRange(0, size - 501);
            }
        });
    }

    private String toFriendlyMessage(RecognitionEvent event) {
        RecognitionEventType type = event.getType();
        return switch (type) {
            case FRAME_PROCESSED, FACE_DETECTED, TRACK_LOST -> null;
            case CAMERA_STARTED -> "Camera started";
            case CAMERA_STOPPED -> "Camera stopped";
            case AUTO_ACCEPTED -> "Marked present automatically for " + describeSubject(event) + '.';
            case AUTO_REJECTED -> formatAutoRejected(event);
            case MANUAL_CONFIRMATION_REQUIRED ->
                    "Please confirm identity for " + describeSubject(event) + '.';
            case MANUAL_CONFIRMED -> "Manual confirmation recorded for " + describeSubject(event) + '.';
            case MANUAL_REJECTED -> "Manual confirmation rejected for " + describeSubject(event) + '.';
            case ATTENDANCE_RECORDED -> "Attendance recorded for " + describeSubject(event) + '.';
            case ATTENDANCE_SKIPPED -> "Skipped " + describeSubject(event) + " — already counted.";
            case ERROR -> formatErrorMessage(event);
        };
    }

    private String describeSubject(RecognitionEvent event) {
        if (event.getStudentName() != null && !event.getStudentName().isBlank()) {
            return event.getStudentName();
        }
        if (event.getStudentId() != null && !event.getStudentId().isBlank()) {
            return "ID " + event.getStudentId();
        }
        if (event.getTrackId() != null && !event.getTrackId().isBlank()) {
            return "track " + event.getTrackId();
        }
        return "the student";
    }

    private String formatAutoRejected(RecognitionEvent event) {
        String base = "Could not verify " + describeSubject(event) + " automatically";
        String extra = sanitizeMessage(event.getMessage());
        if (extra != null) {
            return base + ": " + extra;
        }
        return base + '.';
    }

    private String formatErrorMessage(RecognitionEvent event) {
        String extra = sanitizeMessage(event.getMessage());
        if (extra == null) {
            return "Error: Something went wrong.";
        }
        return "Error: " + extra;
    }

    private String sanitizeMessage(String message) {
        if (message == null) {
            return null;
        }
        String trimmed = message.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        int newline = trimmed.indexOf('\n');
        if (newline >= 0) {
            trimmed = trimmed.substring(0, newline).strip();
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    public boolean promptManualConfirmation(String studentName) {
        final AtomicReference<Boolean> response = new AtomicReference<>(Boolean.FALSE);
        try {
            SwingUtilities.invokeAndWait(() -> {
                int result = javax.swing.JOptionPane.showConfirmDialog(
                        frame,
                        "Is this " + (studentName != null ? studentName : "the correct student") + "?",
                        "Confirm identity",
                        javax.swing.JOptionPane.YES_NO_OPTION,
                        javax.swing.JOptionPane.QUESTION_MESSAGE);
                response.set(result == javax.swing.JOptionPane.YES_OPTION);
            });
        } catch (Exception ignored) {
            return false;
        }
        return Boolean.TRUE.equals(response.get());
    }

    @Override
    public void close() {
        SwingUtilities.invokeLater(() -> {
            frame.dispose();
        });
    }

    private final class OverlayPainter implements WebcamPanel.Painter {
        private final WebcamPanel.Painter delegate = webcamPanel.getDefaultPainter();

        @Override
        public void paintPanel(WebcamPanel panel, Graphics2D g2) {
            delegate.paintPanel(panel, g2);
        }

        @Override
        public void paintImage(WebcamPanel panel, BufferedImage image, Graphics2D graphics) {
            delegate.paintImage(panel, image, graphics);
            if (image == null) {
                return;
            }
            int panelWidth = panel.getWidth();
            int panelHeight = panel.getHeight();
            if (panelWidth <= 0 || panelHeight <= 0) {
                return;
            }
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();
            if (imageWidth <= 0 || imageHeight <= 0) {
                return;
            }

            double scaleX = panelWidth / (double) imageWidth;
            double scaleY = panelHeight / (double) imageHeight;
            boolean fill = panel.isFillArea();
            double scale;
            if (fill) {
                scale = Math.max(scaleX, scaleY);
            } else {
                scale = Math.min(1.0d, Math.min(scaleX, scaleY));
            }
            if (!Double.isFinite(scale) || scale <= 0.0d) {
                return;
            }
            int drawWidth = (int) Math.round(imageWidth * scale);
            int drawHeight = (int) Math.round(imageHeight * scale);
            int offsetX = (panelWidth - drawWidth) / 2;
            int offsetY = (panelHeight - drawHeight) / 2;
            int maxX = offsetX + drawWidth;
            int maxY = offsetY + drawHeight;

            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Collection<TrackedFace> faces = trackedFaces.get();
            for (TrackedFace face : faces) {
                if (face == null || face.track() == null) {
                    continue;
                }
                Rectangle bounds = face.track().getBounds();
                if (bounds == null) {
                    continue;
                }

                int x = offsetX + (int) Math.round(bounds.x * scale);
                int y = offsetY + (int) Math.round(bounds.y * scale);
                int w = Math.max(1, (int) Math.round(bounds.width * scale));
                int h = Math.max(1, (int) Math.round(bounds.height * scale));

                if (x >= maxX || y >= maxY || x + w <= offsetX || y + h <= offsetY) {
                    continue;
                }
                if (x < offsetX) {
                    int delta = offsetX - x;
                    x = offsetX;
                    w -= delta;
                }
                if (y < offsetY) {
                    int delta = offsetY - y;
                    y = offsetY;
                    h -= delta;
                }
                if (x + w > maxX) {
                    w = maxX - x;
                }
                if (y + h > maxY) {
                    h = maxY - y;
                }
                if (w <= 0 || h <= 0) {
                    continue;
                }
                Color color = face.overlayColor();
                graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 90));
                graphics.fillRect(x, y, w, h);

                graphics.setColor(color);
                graphics.setStroke(new BasicStroke(3f));
                graphics.drawRect(x, y, w, h);

                String label = face.studentName() != null ? face.studentName()
                        : face.studentId() != null ? face.studentId()
                        : "Detecting";
                if (Double.isFinite(face.lastConfidence())) {
                    label += String.format(" (%.1f)", face.lastConfidence());
                }
                int labelHeight = 22;
                int labelY = y - labelHeight;
                if (labelY < offsetY) {
                    labelY = Math.min(y + h, maxY - labelHeight);
                    if (labelY < offsetY) {
                        labelY = offsetY;
                    }
                }
                graphics.setColor(new Color(0, 0, 0, 180));
                graphics.fillRect(x, labelY, w, labelHeight);
                graphics.setColor(Color.WHITE);
                graphics.drawString(label, x + 8, labelY + 16);
            }
        }
    }

    private void handleRosterFilterChange() {
        String text = rosterSearchField != null ? rosterSearchField.getText() : null;
        rosterFilter = text != null ? text.trim().toLowerCase(Locale.ROOT) : "";
        refreshRosterList();
    }

    private void refreshRosterList() {
        if (rosterListPanel == null) {
            return;
        }
        rosterListPanel.removeAll();
        List<RosterEntry> filtered = rosterEntries.stream()
                .filter(entry -> matchesFilter(entry, rosterFilter))
                .toList();
        if (filtered.isEmpty()) {
            JLabel emptyLabel = new JLabel("No students to display");
            emptyLabel.setForeground(new Color(170, 174, 184));
            emptyLabel.setBorder(new EmptyBorder(8, 8, 8, 8));
            rosterListPanel.add(emptyLabel);
        } else {
            for (RosterEntry entry : filtered) {
                rosterListPanel.add(createRosterEntryComponent(entry));
                rosterListPanel.add(Box.createVerticalStrut(8));
            }
        }
        rosterListPanel.revalidate();
        rosterListPanel.repaint();
    }

    private JPanel createRosterEntryComponent(RosterEntry entry) {
        JPanel container = new JPanel(new BorderLayout());
        container.setBorder(new EmptyBorder(8, 8, 8, 8));
        container.setBackground(new Color(28, 32, 40));
        container.setOpaque(true);
        container.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);

        String displayName = entry.fullName() != null ? entry.fullName() : "Unknown student";
        JLabel nameLabel = new JLabel(displayName);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
        textPanel.add(nameLabel);

        String idDisplay = entry.studentNumber() != null && !entry.studentNumber().isBlank()
                ? entry.studentNumber()
                : entry.studentId();
        JLabel idLabel = new JLabel("ID: " + (idDisplay != null ? idDisplay : "N/A"));
        idLabel.setForeground(new Color(170, 174, 184));
        textPanel.add(idLabel);

        JLabel statusLabel = new JLabel("Status: " + (entry.status() != null ? entry.status() : "pending"));
        statusLabel.setForeground(colorForStatus(entry.status()));
        textPanel.add(statusLabel);

        String marked = entry.markedAt() != null ? TIME_FORMAT.format(entry.markedAt()) : "—";
        JLabel markedLabel = new JLabel("Marked at: " + marked);
        markedLabel.setForeground(new Color(170, 174, 184));
        textPanel.add(markedLabel);

        String methodDisplay = entry.markingMethod() != null ? entry.markingMethod() : "—";
        JLabel methodLabel = new JLabel("Method: " + methodDisplay);
        methodLabel.setForeground(new Color(170, 174, 184));
        textPanel.add(methodLabel);

        if (entry.confidence() != null && Double.isFinite(entry.confidence())) {
            JLabel confidenceLabel = new JLabel(String.format(Locale.ROOT, "Confidence: %.1f", entry.confidence()));
            confidenceLabel.setForeground(new Color(170, 174, 184));
            textPanel.add(confidenceLabel);
        }

        container.add(textPanel, BorderLayout.CENTER);

        boolean submitting = rosterSubmitting.contains(entry.studentId());
        boolean alreadyMarked = entry.status() != null
                && ("present".equalsIgnoreCase(entry.status()) || "late".equalsIgnoreCase(entry.status()));

        JButton manualButton = new JButton(submitting ? "Marking..." : "Mark manual");
        manualButton.setEnabled(!alreadyMarked && !submitting);
        manualButton.addActionListener(e -> {
            if (manualMarkListener != null && !submitting && !alreadyMarked) {
                manualMarkListener.accept(entry);
            }
        });
        container.add(manualButton, BorderLayout.EAST);

        return container;
    }

    private boolean matchesFilter(RosterEntry entry, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        String normalized = filter.toLowerCase(Locale.ROOT);
        return contains(normalized, entry.fullName())
                || contains(normalized, entry.studentNumber())
                || contains(normalized, entry.studentId());
    }

    private boolean contains(String needle, String value) {
        if (needle == null || value == null) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private Color colorForStatus(String status) {
        if (status == null) {
            return new Color(170, 174, 184);
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "present" -> new Color(16, 158, 72);
            case "late" -> new Color(199, 128, 27);
            case "absent" -> new Color(170, 50, 50);
            default -> new Color(170, 174, 184);
        };
    }

    public static final record RosterEntry(String studentId,
                                           String fullName,
                                           String studentNumber,
                                           String status,
                                           Instant markedAt,
                                           String markingMethod,
                                           Double confidence) {
    }
}
