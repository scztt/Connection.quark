+View {
	autoReleaseDependants {
		this.onClose = this.onClose.add({ |v| v.releaseDependants });
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