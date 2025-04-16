+View {
    updateOnClose {
        |should=true|
        if (should) {
            ViewActionUpdater.enable(this, \onClose, \isClosed, \closed);
        } {
            ViewActionUpdater.disable(this, \onClose, \isClosed, \closed);
        }
    }
    
    updateOnAction {
        |should=true, property=\value|
        if (should) {
            ViewActionUpdater.enable(this, propertyName: property, signalName: property);
        } {
            ViewActionUpdater.disable(this);
        }
    }
    
    signal {
        |key|
        // automatically update on action if we connect to a View
        switch (key,
            \value, { this.updateOnAction(property:\value) },
            \lo, { this.updateOnAction(property:\lo) },
            \hi, { this.updateOnAction(property:\hi) },
            \closed, {
                this.updateOnClose();
            },
        );
        
        ^super.signal(key);
    }
}
