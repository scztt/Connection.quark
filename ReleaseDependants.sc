+View {
	autoReleaseDependants {
		this.onClose = this.onClose.addFunc({ |v| v.releaseDependants });
	}
}

+Node {
	autoReleaseDependants {
		this.onFree({ |n| n.releaseDependants });
	}
}

+NodeProxy {
	autoReleaseDependants {
		this.group.onFree({
			this.releaseDependants
		});
	}
}