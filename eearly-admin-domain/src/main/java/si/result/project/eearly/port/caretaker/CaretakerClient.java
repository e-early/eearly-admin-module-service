package si.result.project.eearly.port.caretaker;

import si.result.project.eearly.model.caretaker.Caretaker;

public interface CaretakerClient {
    void upsertCaretaker(Caretaker caretaker);
}
