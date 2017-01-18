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
		|should=true|
		if (should) {
			ViewActionUpdater.enable(this);
		} {
			ViewActionUpdater.disable(this);
		}
	}

	signal {
		|key|
		// automatically update on action if we connect to a View
		switch (key,
			\value, {
				this.updateOnAction();
			},
			\closed, {
				this.updateOnClose();
		});

		^super.signal(key);
	}
}
