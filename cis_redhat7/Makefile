DEST_DIR=target
BUILD_DIR=build

include ../makefiles/common.mk

# the metadata setup is not overriden and so, is not included in $|
$(FULL_NAME)-$(VERSION).rpkg: $(DEST_DIR)/metadata \
  $(DEST_DIR)/src.txz     \
  $(DEST_DIR)/ncf.txz     \
  $(DEST_DIR)/scripts.txz
	ar r $(FULL_NAME)-$(VERSION).rpkg $(DEST_DIR)/metadata $(DEST_DIR)/src.txz $(DEST_DIR)/ncf.txz $(DEST_DIR)/scripts.txz

target/files.txz: ;

$(DEST_DIR)/ncf.txz: $(BUILD_DIR)
	mkdir -p $(DEST_DIR)
	tar cJ -C $(BUILD_DIR)/src -f $(DEST_DIR)/ncf.txz ncf

$(DEST_DIR)/scripts.txz: $(BUILD_DIR)
	mkdir -p $(DEST_DIR)
	tar cJ -C $(BUILD_DIR)/packaging -f $(DEST_DIR)/scripts.txz .

$(DEST_DIR)/src.txz: $(BUILD_DIR)
	mkdir -p $(DEST_DIR)
	tar cJ -C $(BUILD_DIR) -f $(DEST_DIR)/src.txz src/technique.rd

$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)
	cp -r src $(BUILD_DIR)/
	cp -r packaging $(BUILD_DIR)/
	./tools/tags.sh $(BUILD_DIR)/src/technique.rd $(BUILD_DIR)/src/item_list

clean:
	rm -rf $(DEST_DIR) $(BUILD_DIR) *.rpkg
