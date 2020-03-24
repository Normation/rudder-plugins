BUILDABLE = redhat7 debian9 ubuntu18
.PHONY: all halfclean clean $(BUILDABLE)
.DEFAULT_GOAL := all

all:
	for bench in $(BUILDABLE) ; do \
    $(MAKE) halfclean && $(MAKE) $$bench ; \
done

# build.conf has to be edited before calling the specific makefile
# otherwise the vars are wrongly initialized
build.conf:
	cp -ar src/build.conf build.conf
	sed -i -e "s/{plugin-id\}/cis-$(OS)/g" build.conf

debian9: OS = debian9
debian9: build.conf
	$(MAKE) -f Makefile.plugin debian9

ubuntu18: OS = ubuntu18
ubuntu18: build.conf
	$(MAKE) -f Makefile.plugin ubuntu18

redhat7: OS = redhat7
redhat7: build.conf
	$(MAKE) -f Makefile.plugin redhat7

halfclean:
	$(MAKE) -f Makefile.plugin halfclean

clean:
	$(MAKE) -f Makefile.plugin clean
