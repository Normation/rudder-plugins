@format = 0
@component = "CIS_rhel7"
@description = "Audit rhel7 configuration based on CIS Benchmark"
@version = "1.0"
@category = "system"
@parameters = [
@  { "name" = "skip_list", "id" = "TODO", "description" = "List of recommandations not to audit" }
@]

global enum CIS_Applicability = {
  CIS_Workstation,
  CIS_Server
}
items in CIS_Workstation {
	# Workstation Level 1
  WL1,
  # Workstation Level 2
  WL2
}
items in CIS_Server {
  # Server Level 1
  SL1,
  # Server Level 2
  SL2
}

## Generic resource calls

resource CIS_service(service)

CIS_service state disabled() {
  service(service).disabled()
  service(service).stopped()
}
CIS_service state enabled() {
  service(service).enabled()
  service(service).started()
}

resource CIS_package(package, posthook)
CIS_package state installed() {
  package(package).present("any", "default", "default") as package_present
  if package_present =~ repaired => command(posthook).execution()
}
CIS_package state not_installed(prerm, check_command) {
  let cdt = condition("cis").from_command(check_command, 0, 999)
  if !cdt => command(prerm).execution()
  package(package).absent("any", "default", "default") as pkg_absent
  if pkg_absent =~ error => command(posthook).execution()
}

resource CIS_variable_from_command(name)
CIS_variable state match_from_command(command, regex) {
  variable("technique_cis_variable", name).string_from_command(command)
  variable("technique_cis_variable", name).string_match(regex)
}

## CIS control point categories

# 1
resource CIS_rhel7_setup()
resource CIS_rhel7_setup_filesystem()
resource CIS_rhel7_setup_software_updates()
resource CIS_rhel7_setup_boot()
resource CIS_rhel7_setup_process()
resource CIS_rhel7_setup_MAC()
resource CIS_rhel7_setup_warning_banners()

#2
resource CIS_rhel7_services()

#3
resource CIS_rhel7_network()

#4
resource CIS_rhel7_logging_and_auditing()

#5
resource CIS_rhel7_access_authentification_authorization()

#6
resource CIS_rhel7_system_maintenance()

CIS state rhel7() {
  @index = "1"
  @component = "Initial Setup"
  CIS_rhel7_setup().configured()

  @index = "2"
  @component = "Services"
  CIS_rhel7_services().configured()

  @index = "3"
  @component = "Network Configuration"
  CIS_rhel7_network().configured()

  @index = "4"
  @component = "Logging and Auditing"
  CIS_rhel7_logging_and_auditing()().configured()

  @index = "5"
  @component = "Access, Authentication and Authorization"
  CIS_rhel7_access_authentification_authorization().configured()

  @index = "6"
  @component = "System Maintenance"
  CIS_rhel7_system_maintenance().configured()
}

CIS_rhel7_setup state configured() {
  CIS_rhel7_setup_filesystem().configured()
  CIS_rhel7_setup_software_updates().configured()
  CIS_rhel7_setup_filesystem().integrity()
  CIS_rhel7_setup_boot().secured_boot_configured()
  CIS_rhel7_setup_process().hardened()
  CIS_rhel7_setup_MAC().SELinux_configured()
  CIS_rhel7_setup_MAC().SELinux_installed()
  CIS_rhel7_setup_warning_banners().commands()
  CIS_rhel7_setup_warning_banners().configured()
  CIS_rhel7_setup_software_updates().checked()
}

CIS_rhel7_services state configured() {
  @index = "2.1"
  @component = "inetd Services"
  CIS_rhel7_services().inetd_configured()

  @index = "2.2"
  @component = "special Purpose Services"
  CIS_rhel7_services().special_purpose_services_configured()

  @index = "3.1"
  @component = "Network Parameters (Host Only)"
  CIS_rhel7_network().host_only_parameters_configured()

  @index = "3.2"
  @component = "Network Parameters (Host and Router)"
  CIS_rhel7_network().host_and_router_parameters_configured()

  @index = "3.3"
  @component = "ipv6"
  CIS_rhel7_network().ipv6_configured()

  @index = "3.4"
  @component = "TCP wrappers"
  CIS_rhel7_network().tcp_wrappers_configured()

  @index = "3.5"
  @component = "Uncommon network protocols"
  CIS_rhel7_network().uncommon_network_protocols_configured()

  @index = "3.6"
  @component = "Firewall Configuration"
  CIS_rhel7_network().firewall_configured()

  # is it really doable manually? iwconfig / ip link show up
  let is_to_skip = condition_from_list_variable_match("skip_list", "3.7")
  @index = "3.7"
  @component = "Ensure wireless interfaces are disabled"
  @applicability = [ "SL1", "WL2" ]
  @is_scored = false
  if ((SL1 | WL2) & !is_to_skip) => log_info "3.7 (Ensure wireless interfaces are disabled) is not scored. Please configure the test manually if needed."

  @index = "4.1"
  @component = "Configure System Accounting (auditd)"
  CIS_rhel7_logging_and_auditing().system_accounting_configured()

  @index = "4.2"
  @component = "Configure Logging"
  CIS_rhel7_logging_and_auditing().logging_configured()

  let is_to_skip = condition_from_list_variable_match("skip_list", "4.3")
  @index = "4.3"
  @component = "Ensure logrotate is configured"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = false
  if ((SL1 | WL1) & !is_to_skip) => log_info "4.3 (Ensure logrotate is configured) is not scored. Please configure the test manually if needed."

  @index = "5.1"
  @component = "Configure cron"
  CIS_rhel7_access_authentification_authorization().cron_configured()

  @index = "5.2"
  @component = "SSH Server Configuration"
  CIS_rhel7_access_authentification_authorization().ssh_server_configured()

  @index = "5.3"
  @component = "Configure PAM"
  CIS_rhel7_access_authentification_authorization().pam_configured()

  @index = "5.4"
  @component = "User Accounts and Environment"
  CIS_rhel7_access_authentification_authorization().user_accounts_and_environment_configured()

  @index = "6.1"
  @component = "System File Permissions"
  CIS_rhel7_system_maintenance().system_file_permissions_configured()

  @index = "6.2"
  @component = "User and Group Settings"
  CIS_rhel7_system_maintenance().user_and_group_settings_configured()
}

@index = "1.1"
CIS_rhel7_setup_filesystem state configured() {
  let is_to_skip = condition_from_list_variable_match("skip_list", "1.1.1.1")
  @index = "1.1.1.1"
  @component = "Ensure mounting of cramfs filesystems is disabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_service("cramfs").disabled()

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.1.1.2")
  @index = "1.1.1.2"
  @component = "Ensure mounting of freevxfs filesystems is disabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip SkipList) => CIS_service("freevxfs").disabled()

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.1.1.3")
  @index = "1.1.1.3"
  @component = "Ensure mounting of jffs2 filesystems is disabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip SkipList) => CIS_service("jjffs2").disabled()

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.1.1.4")
  @index = "1.1.1.4"
  @component = "Ensure mounting of hfs filesystems is disabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip SkipList) => CIS_service("hfs").disabled()

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.1.1.5")
  @index = "1.1.1.5"
  @component = "Ensure mounting of hfsplus filesystems is disabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip SkipList) => CIS_service("hfsplus").disabled()

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.1.1.6")
  @index = "1.1.1.6"
  @component = "Ensure mounting of squashfs filesystem is disabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip SkipList) => CIS_service("squashfs").disabled()

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.1.1.7")
  @index = "1.1.1.7"
  @component = "Ensure mounting of udf filesystem is disabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip SkipList) => CIS_service("udf").disabled()

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.1.1.8")
  @index = "1.1.1.8"
  @component = "Ensure mounting of udf filesystem is disabled"
  @applicability = [ "SL1", "WL2" ]
  @is_scored = true
  if ((SL1 || WL2) & !is_to_skip SkipList) => CIS_service("vfat").disabled()

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.1.2")
  @index = "1.1.2"
  @component = "Ensure separate partition exists for /tmp"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  if ((SL2 || WL2) & !is_to_skip) => partition("/tmp").check_mounted()

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.1.3")
  @index = "1.1.3"
  @component = "Ensure nodev option set on /tmp partition"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => partition("/tmp").check_options("nodev")

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.1.4")
  @index = "1.1.4"
  @component = "Ensure nosuid option set on /tmp partition"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => partition("/tmp").check_options("nosuid")

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.1.5")
  @index = "1.1.5"
  @component = "Ensure noexec option set on /tmp partition"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => partition("/tmp").check_options("noexec")

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.1.6")
  @index = "1.1.6"
  @component = "Ensure separate partition exists for /var"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  if ((SL2 || WL2) & !is_to_skip) => partition("/var").check_mounted()

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.1.7")
  @index = "1.1.7"
  @component = "Ensure separate partition exists for /var/tmp"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  if ((SL2 || WL2) & !is_to_skip) => partition("/var/tmp").check_mounted()

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.1.8")
  @index = "1.1.8"
  @component = "Ensure nodev option set on /var/tmp partition"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => partition("/var/tmp").check_options("nodev")

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.1.9")
  @index = "1.1.9"
  @component = "Ensure nosuid option set on /var/tmp partition"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => partition("/var/tmp").check_options("nosuid")

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.1.10")
  @index = "1.1.10"
  @component = "Ensure noexec option set on /var/tmp partition"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => partition("/var/tmp").check_options("noexec")

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.1.11")
  @index = "1.1.11"
  @component = "Ensure separate partition exists for /var/log"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  if ((SL2 || WL2) & !is_to_skip) => partition("/var/log").check_mounted()

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.1.12")
  @index = "1.1.12"
  @component = "Ensure separate partition exists for /var/log/audit"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  if ((SL2 || WL2) & !is_to_skip) => partition("/var/log/audit").check_mounted()

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.1.13")
  @index = "1.1.13"
  @component = "Ensure separate partition exists for /home"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  if ((SL2 || WL2) & !is_to_skip) => partition("/home").check_mounted()

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.1.14")
  @index = "1.1.14"
  @component = "Ensure nodev option set on /home partition"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => partition("/home").check_options("nodev")

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.1.15")
  @index = "1.1.15"
  @component = "Ensure nodev option set on /dev/shm partition"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => partition("/dev/shm").check_options("nodev")

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.1.16")
  @index = "1.1.16"
  @component = "Ensure nosuid option set on /dev/shm partition"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => partition("/dev/shm").check_options("nosuid")

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.1.17")
  @index = "1.1.17"
  @component = "Ensure noexec option set on /dev/shm partition"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => partition("/dev/shm").check_options("noexec")

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.1.18")
  @index = "1.1.18"
  @component = "Ensure nodev option set on removable media partitions"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = false
  if ((SL1 | WL1) & !is_to_skip) => log_info "1.1.18 (Ensure nodev option set on removable media partitions) is not scored. Please configure the test manually if needed."

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.1.19")
  @index = "1.1.19"
  @component = "Ensure nosuid option set on removable media partitions"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = false
  if ((SL1 | WL1) & !is_to_skip) => log_info "1.1.19 (Ensure nosuid option set on removable media partitions) is not scored. Please configure the test manually if needed."

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.1.20")
  @index = "1.1.20"
  @component = "Ensure noexec option set on removable media partitions"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = false
  if ((SL1 | WL1) & !is_to_skip) => log_info "1.1.20 (Ensure noexec option set on removable media partitions) is not scored. Please configure the test manually if needed."

  # 1.1.21 must be implemented by hand since going through all word-writable could take a while
  # Maybe call the method that generate a NA report.
  let is_to_skip = condition_from_list_variable_match("skip_list", "1.1.21")
  @index = "1.1.21"
  @component = "Ensure automounting is disabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => log_warn "1.1.21 (Ensure automounting is disabled) should be audited manually since going through all word-writable could take a while"


  let is_to_skip = condition_from_list_variable_match("skip_list", "1.1.22")
  @index = "1.1.22"
  @component = "Ensure automounting is disabled"
  @applicability = [ "SL1", "WL2" ]
  @is_scored = true
  if ((SL1 | WL2) & !is_to_skip) => CIS_service("autofs").disabled()
}


@index = "1.2"
CIS_rhel7_setup_software_updates state configured() {
  let is_to_skip = condition_from_list_variable_match("skip_list", "1.2.1")
  @index = "1.2.1"
  @component = "Ensure package manager repositories are configured"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = false
  if ((SL1 | WL1) && !"1.2.2" in SkipList) => log_info "1.2.1 (Ensure package manager repositories are configured) is not scored. Please configure the test manually if needed."

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.2.2")
  @index = "1.2.2"
  @component = "Ensure gpgcheck is globally activated"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) && !"1.2.2" in SkipList) => {
    CIS_variable("gpgcheck_yum_conf").match_from_command("grep ^gpgcheck /etc/yum.conf", "gpgcheck=1")
    CIS_variable("gpgcheck_yum_repos_d").match_from_command("grep ^gpgcheck /etc/yum.repos.d/*", "^(gpgcheck=1\s+?)+$")
  }

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.2.3")
  @index = "1.2.3"
  @component = "Ensure GPG keys are configured"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = false
  if ((SL1 | WL1) && !"1.2.2" in SkipList) => log_info "1.2.3 (Ensure GPG keys are configured) is not scored. Please configure the test manually if needed."

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.2.4")
  @index = "1.2.4"
  @component = "Ensure Red Hat Network or Subscription Manager connection is configured"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = false
  if ((SL1 | WL1) && !"1.2.2" in SkipList) => log_info "1.2.4 (Ensure Red Hat Network or Subscription Manager connection is configured) is not scored. Please configure the test manually if needed."

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.2.5")
  @index = "1.2.5"
  @component = "Disable the rhnsd Daemon"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = false
  if ((SL1 | WL1) && !"1.2.2" in SkipList) => log_info "1.2.5 (Disable the rhnsd Daemon) is not scored. Please configure the test manually if needed."
}


@index = "1.3"
CIS_rhel7_setup_filesystem state integrity() {
  let is_to_skip = condition_from_list_variable_match("skip_list", "1.3.1")
  @index = "1.3.1"
  @component = "Ensure AIDE is installed"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_variable("rpm_q_aide").match_from_command("rpm -q aide", "aide-")

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.3.2")
  @index = "1.3.2"
  @component = "Ensure filesystem integrity is regularly checked"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => {
    CIS_variable("crontab_u_root").match_from_command("crontab -u root -l | grep aide", "aide")
    CIS_variable("grep_crontab_aide").match_from_command("grep -r aide /etc/cron.* /etc/crontab", "aide")
  }
}

@index = "1.4"
CIS_rhel7_setup_boot state secured_boot_configured_configured() {
  let is_to_skip = condition_from_list_variable_match("skip_list", "1.4.1")
  @index = "1.4.1"
  @component = "Ensure filesystem integrity is regularly checked"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  # could directly check file content
  if ((SL1 | WL1) & !is_to_skip) => permissions("/boot/grub2/grub.cfg").value(600, root, root)

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.4.2")
  @index = "1.4.2"
  @component = "Ensure bootloader password is set"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => log_warn "1.4.2 (Ensure bootloader password is set) must be set manually since it requires an username"

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.4.3")
  @index = "1.4.3"
  @component = "Ensure authentication required for single user mode"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = false
  if ((SL1 | WL1) & !is_to_skip) => log_info "1.4.3 (Ensure authentication required for single user mode) is not scored. Please configure the test manually if needed."
}

@index = "1.5"
CIS_rhel7_setup_process state hardened() {
  let is_to_skip = condition_from_list_variable_match("skip_list", "1.5.1")
  @index = "1.5.1"
  @component = "Ensure core dumps are restricted"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => {
    CIS_variable("hard_core_dumps").match_from_command("""grep "hard core" /etc/security/limits.conf /etc/security/limits.d/*""", ".*\* hard core 0.*")
    sysctl("fs.suid_dumpable").value(0, "suid_dumpable")
  }

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.5.2")
  @index = "1.5.2"
  @component = "Ensure XD/NX support is enabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = false
  if ((SL1 | WL1) & !is_to_skip) => log_info "1.5.2 (Ensure XD/NX support is enabled) is not scored. Please configure the test manually if needed."

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.5.3")
  @index = "1.5.3"
  @component = "Ensure address space layout randomization (ASLR) is enabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => sysctl("kernel.randomize_va_space").value(3, "randomize_va_space")

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.5.4")
  @index = "1.5.4"
  @component = "Ensure prelink is disabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_package("prelink").not_installed()
}

# maybe there should only be a state SELinux that would contain both configuration and installation
@index = "1.6.1"
CIS_rhel7_setup_MAC state SELinux_configured() {
  let is_to_skip = condition_from_list_variable_match("skip_list", "1.6.1.1")
  @index = "1.6.1.1"
  @component = "Ensure SELinux is not disabled in bootloader configuration"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  if ((SL2 | WL2) & !is_to_skip) => CIS_variable("selinux_not_disabled").match_from_command("""grep "^\s*linux" /boot/grub2/grub.cfg""", ".*(selinux|enforcing)\s*=\s*0.*")

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.6.1.2")
  @index = "1.6.1.2"
  @component = "Ensure the SELinux state is enforcing"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  # maybe we should also check sestatus command
  if ((SL2 | WL2) & !is_to_skip) => file("/etc/selinux/config").key_value_present("SELINUX", "enforcing", "=")

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.6.1.3")
  @index = "1.6.1.3"
  @component = "Ensure SELinux policy is configured"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  # maybe we should also check sestatus command
  if ((SL2 | WL2) & !is_to_skip) => file("/etc/selinux/config").key_value_present("SELINUXTYPE", "targeted", "=")

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.6.1.4")
  @index = "1.6.1.4"
  @component = "Ensure SETroubleshoot is not installed"
  @applicability = [ "SL2" ]
  @is_scored = true
  if (SL2 & !is_to_skip) => CIS_package("setroubleshoot").not_installed()

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.6.1.5")
  @index = "1.6.1.5"
  @component = "Ensure the MCS Translation Service (mcstrans) is not installed"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  if ((SL2 | WL2) & !is_to_skip) => CIS_package("mcstrans").not_installed()

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.6.1.6")
  @index = "1.6.1.6"
  @component = "Ensure no unconfined daemons exist"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  # ensure is empty
  if ((SL2 | WL2) & !is_to_skip) => CIS_variable("no_unconfined_daemons").match_from_command("""ps -eZ | egrep "initrc" | egrep -vw "tr|ps|egrep|bash|awk" | tr ":" " " | awk "{ print $NF }"""", "^\s*$")
}

@index = "1.6.2"
CIS_rhel7_setup_MAC state SELinux_installed() {
  let is_to_skip = condition_from_list_variable_match("skip_list", "1.6.2")
  @index = "1.6.2"
  @component = "Ensure SELinux is installed"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  if ((SL2 | WL2) & !is_to_skip) => CIS_package("libselinux").not_installed()
}

@index = "1.7.1"
CIS_rhel7_setup_warning_banners state commands() {
  let is_to_skip = condition_from_list_variable_match("skip_list", "1.7.1.1")
  @index = "1.7.1.1"
  @component = "Ensure message of the day is configured properly"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  ## also manually check contents of `cat /etc/motd` matches site policy
  if ((SL1 | WL1) & !is_to_skip) => CIS_variable("motd_configured_properly").match_from_command("""egrep "(\\v|\\r|\\m|\\s)" /etc/motd""", "^\s*$")

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.7.1.2")
  @index = "1.7.1.2"
  @component = "Ensure local login warning banner is configured properly"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = false
  ## also manually check contents of `cat /etc/issue` matches site policy
  if ((SL1 | WL1) & !is_to_skip) => CIS_variable("motd_configured_properly").match_from_command("""egrep "(\\v|\\r|\\m|\\s)" /etc/issue""", "^\s*$")

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.7.1.3")
  @index = "1.7.1.3"
  @component = "Ensure remote login warning banner is configured properly"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = false
  ## also manually check contents of `cat /etc/issue.net` matches site policy
  if ((SL1 | WL1) & !is_to_skip) => CIS_variable("motd_configured_properly").match_from_command("""egrep "(\\v|\\r|\\m|\\s)" /etc/issue.net""", "^\s*$")
  
  let is_to_skip = condition_from_list_variable_match("skip_list", "1.7.1.4")
  @index = "1.7.1.4"
  @component = "Ensure permissions on /etc/motd are configured"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = false
  if ((SL1 | WL1) & !is_to_skip) => permissions("/etc/issue").value(644, root, root)

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.7.1.5")
  @index = "1.7.1.5"
  @component = "Ensure permissions on /etc/issue are configured"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => permissions("/etc/issue").value(644, root, root)

  let is_to_skip = condition_from_list_variable_match("skip_list", "1.7.1.6")
  @index = "1.7.1.6"
  @component = "Ensure permissions on /etc/issue.net are configured"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = false
  if ((SL1 | WL1) & !is_to_skip) => permissions("/etc/issue.net").value(644, root, root)
}

# TODO
@index = "1.7.2"
CIS_rhel7_setup_warning_banners state configured() {
  let is_to_skip = condition_from_list_variable_match("skip_list", "1.7.2")
  @index = "1.7.2"
  @component = "Ensure GDM login banner is configured"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_package("libselinux").not_installed()
}

@index = "1.8"
CIS_rhel7_setup_software_updates state checked() {
  let is_to_skip = condition_from_list_variable_match("skip_list", "1.8")
  @index = "1.8"
  @component = "Ensure GDM login banner is configured"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = false
  if ((SL1 | WL1) & !is_to_skip) => CIS_variable("yum_check_update").match_from_command("yum check-update", "^\s*$")
}

CIS_rhel7_services state inetd_pair_services_configured(dgram, stream) {
  CIS_service(dgram).disabled()
  CIS_service(stream).disabled()
}
CIS_rhel7_services state inetd_configured() {
  let is_to_skip = condition_from_list_variable_match("skip_list", "2.1.1")
  @index = "2.1.1"
  @component = "Ensure chargen services are not enabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_rhel7_services().inetd_pair_services_configured("chargen-dgram", "chargen-stream")

  let is_to_skip = condition_from_list_variable_match("skip_list", "2.1.2")
  @index = "2.1.2"
  @component = "Ensure daytime services are not enabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_rhel7_services().inetd_pair_services_configured("daytime-dgram", "daytime-stream")

  let is_to_skip = condition_from_list_variable_match("skip_list", "2.1.3")
  @index = "2.1.3"
  @component = "Ensure discard services are not enabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_rhel7_services().inetd_pair_services_configured("discard-dgram", "discard-stream")

  let is_to_skip = condition_from_list_variable_match("skip_list", "2.1.4")
  @index = "2.1.4"
  @component = "Ensure echo services are not enabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_rhel7_services().inetd_pair_services_configured("echo-dgram", "echo-stream")

  let is_to_skip = condition_from_list_variable_match("skip_list", "2.1.5")
  @index = "2.1.5"
  @component = "Ensure time services are not enabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_rhel7_services().inetd_pair_services_configured("time-dgram", "time-stream")

  let is_to_skip = condition_from_list_variable_match("skip_list", "2.1.6")
  @index = "2.1.6"
  @component = "Ensure tftp server is not enabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_service("tftp").disabled()

  # TODO check if this GM works for `systemctl is-enabled xinetd` command
  let is_to_skip = condition_from_list_variable_match("skip_list", "2.1.7")
  @index = "2.1.7"
  @component = "Ensure xinetd is not enabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_service("xinetd").disabled()
}

@index = "2.2"
CIS_rhel7_services state special_purpose_services_configured() {
  @index = "2.2.1"
  @component = "Time synchronization services"
  CIS_rhel7_services().time_synchronized() {

  # TODO: ensure asterisque works
  let is_to_skip = condition_from_list_variable_match("skip_list", "2.2.2")
  @index = "2.2.2"
  @component = "Ensure X window system not installed"
  @applicability = [ "SL1" ]
  @is_scored = true
  if (SL1 & !is_to_skip) => CIS_package("xorg-x11*").not_installed()

  let is_to_skip = condition_from_list_variable_match("skip_list", "2.2.3")
  @index = "2.2.3"
  @component = "Ensure Avahi Server is not enabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_service("avahi-daemon").disabled()

  let is_to_skip = condition_from_list_variable_match("skip_list", "2.2.4")
  @index = "2.2.4"
  @component = "Ensure CUPS is not enabled"
  @applicability = [ "SL1", "WL2" ]
  @is_scored = true
  if ((SL1 | WL2) & !is_to_skip) => CIS_service("cups").disabled()

  let is_to_skip = condition_from_list_variable_match("skip_list", "2.2.5")
  @index = "2.2.5"
  @component = "Ensure DHCP Server is not enabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_service("dhcpd").disabled()

  let is_to_skip = condition_from_list_variable_match("skip_list", "2.2.6")
  @index = "2.2.6"
  @component = "Ensure LDAP server is not enabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_service("sldapd").disabled()

  let is_to_skip = condition_from_list_variable_match("skip_list", "2.2.7")
  @index = "2.2.7"
  @component = "Ensure NFS and RPC are not enabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => {
    CIS_service("nfs").disabled()
    CIS_service("rpcbind").disabled()
  }

  let is_to_skip = condition_from_list_variable_match("skip_list", "2.2.8")
  @index = "2.2.8"
  @component = "Ensure DNS Server is not enabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_service("named").disabled()

  let is_to_skip = condition_from_list_variable_match("skip_list", "2.2.9")
  @index = "2.2.9"
  @component = "Ensure FTP Server is not enabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_service("vsftpd").disabled()
  
  let is_to_skip = condition_from_list_variable_match("skip_list", "2.2.10")
  @index = "2.2.10"
  @component = "Ensure HTTP Server is not enabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_service("httpd").disabled()
  
  let is_to_skip = condition_from_list_variable_match("skip_list", "2.2.11")
  @index = "2.2.11"
  @component = "Ensure IMAP and POP3 server is not enabled "
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_service("dovecot").disabled()

  let is_to_skip = condition_from_list_variable_match("skip_list", "2.2.12")
  @index = "2.2.12"
  @component = "Ensure Samba is not enabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_service("smb").disabled()
  
  let is_to_skip = condition_from_list_variable_match("skip_list", "2.2.13")
  @index = "2.2.13"
  @component = "Ensure HTTP Proxy Server is not enabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_service("squid").disabled()
  
  let is_to_skip = condition_from_list_variable_match("skip_list", "2.2.14")
  @index = "2.2.14"
  @component = "Ensure SNMP Server is not enabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_service("snmpd").disabled()

  # TODO CHECK
  let is_to_skip = condition_from_list_variable_match("skip_list", "2.2.15")
  @index = "2.2.15"
  @component = "Ensure mail transfer agent is configured for local-only mode"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_variable("MTA_not_listening").match_from_command("""netstat -an | grep LIST | grep ":25[[:space:]]"""", "tcp\s0\s0\s127\.0\.0\.1:25\s0\.0\.0\.0:\*\sLISTEN")

  let is_to_skip = condition_from_list_variable_match("skip_list", "2.2.16")
  @index = "2.2.16"
  @component = "Ensure NIS Server is not enabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_service("ypserv").disabled()

  let is_to_skip = condition_from_list_variable_match("skip_list", "2.2.17")
  @index = "2.2.17"
  @component = "Ensure rsh server is not enabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => {
    CIS_service("rsh.socket").disabled()
    CIS_service("rlogin.socket").disabled()
    CIS_service("rexec.socket").disabled()
  }

  let is_to_skip = condition_from_list_variable_match("skip_list", "2.2.18")
  @index = "2.2.18"
  @component = "Ensure talk server is not enabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_service("ntalk").disabled()

  let is_to_skip = condition_from_list_variable_match("skip_list", "2.2.19")
  @index = "2.2.19"
  @component = "Ensure telnet server is not enabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_service("telnet.socket").disabled()
  
  let is_to_skip = condition_from_list_variable_match("skip_list", "2.2.20")
  @index = "2.2.20"
  @component = "Ensure tftp server is not enabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_service("tftp.socket").disabled()

  let is_to_skip = condition_from_list_variable_match("skip_list", "2.2.21")
  @index = "2.2.21"
  @component = "Ensure rsync service is not enabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_service("rsyncd").disabled()
}

@index = "2.3"
CIS_rhel7_services state clients_services_configured() {
  let is_to_skip = condition_from_list_variable_match("skip_list", "2.3.1")
  @index = "2.3.1"
  @component = "Ensure NIS Client is not installed"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_package("ypbind").not_installed()

  let is_to_skip = condition_from_list_variable_match("skip_list", "2.3.2")
  @index = "2.3.2"
  @component = "Ensure rsh Client is not installed"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_package("rsh").not_installed()

  let is_to_skip = condition_from_list_variable_match("skip_list", "2.3.3")
  @index = "2.3.3"
  @component = "Ensure talk client is not installed"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_package("talk").not_installed()
  
  let is_to_skip = condition_from_list_variable_match("skip_list", "2.3.4")
  @index = "2.3.4"
  @component = "Ensure telnet client is not installed"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_package("telnet").not_installed()

  let is_to_skip = condition_from_list_variable_match("skip_list", "2.3.5")
  @index = "2.3.5"
  @component = "Ensure LDAP client is not installed"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_package("openldap-clients").not_installed()
}

CIS_rhel7_network state packet_redirect_sending_disabled() 
@index = "3.1"
CIS_rhel7_network state host_only_parameters_configured() {
  let is_to_skip = condition_from_list_variable_match("skip_list", "3.1.1")
  @index = "3.1.1"
  @component = "Ensure IP forwarding is disabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => sysctl("net.ipv4.ip_forward").value(0, "ip_forward")

  let is_to_skip = condition_from_list_variable_match("skip_list", "3.1.2")
  @index = "3.1.2"
  @component = "Ensure packet redirect sending is disabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => {
    sysctl("net.ipv4.conf.all.send_redirects").value(0, "all_send_redirects")
    sysctl("net.ipv4.conf.default.send_redirects").value(0, "default_send_redirects")
  }
}

@index = "3.2"
CIS_rhel7_network state host_and_router_parameters_configured() {
  let is_to_skip = condition_from_list_variable_match("skip_list", "3.2.1")
  @index = "3.2.1"
  @component = "Ensure source routed packets are not accepted"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => {
    sysctl("net.ipv4.conf.all.accept_source_route").value(0, "all_source_route")
    sysctl("net.ipv4.conf.default.accept_source_route").value(0, "default_source_route")
  }

  let is_to_skip = condition_from_list_variable_match("skip_list", "3.2.2")
  @index = "3.2.2"
  @component = "Ensure ICMP redirects are not accepted"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => {
    sysctl("net.ipv4.conf.all.accept_redirects").value(0, "all_accept_redirects")
    sysctl("net.ipv4.conf.default.accept_redirects").value(0, "default_accept_redirects")
  }

  let is_to_skip = condition_from_list_variable_match("skip_list", "3.2.3")
  @index = "3.2.3"
  @component = "Ensure secure ICMP redirects are not accepted"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => {
    sysctl("net.ipv4.conf.all.secure_redirects").value(0, "all_secure_redirects")
    sysctl("net.ipv4.conf.default.secure_redirects").value(0, "default_secure_redirects")
  }

  let is_to_skip = condition_from_list_variable_match("skip_list", "3.2.4")
  @index = "3.2.4"
  @component = "Ensure suspicious packets are logged"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => {
    sysctl("net.ipv4.conf.all.log_martians").value(1, "all_log_martians")
    sysctl("net.ipv4.conf.default.log_martians").value(1, "default_log_martians")
  }

  let is_to_skip = condition_from_list_variable_match("skip_list", "3.2.5")
  @index = "3.2.5"
  @component = "Ensure broadcast ICMP requests are ignored"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => sysctl("net.ipv4.icmp_echo_ignore_broadcasts").value(1, "icmp_echo_ignore_broadcasts")

  let is_to_skip = condition_from_list_variable_match("skip_list", "3.2.6")
  @index = "3.2.6"
  @component = "Ensure bogus ICMP responses are ignored"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => sysctl("net.ipv4.icmp_ignore_bogus_error_responses").value(1, "icmp_ignore_bogus_error_responses")

  let is_to_skip = condition_from_list_variable_match("skip_list", "3.2.7")
  @index = "3.2.7"
  @component = "Ensure Reverse Path Filtering is enabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => {
    sysctl("net.ipv4.conf.all.rp_filter").value(1, "all_rp_filter")
    sysctl("net.ipv4.conf.default.rp_filter").value(1, "default_rp_filter")
  }

  let is_to_skip = condition_from_list_variable_match("skip_list", "3.2.8")
  @index = "3.2.8"
  @component = "Ensure TCP SYN Cookies is enabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => sysctl("net.ipv4.tcp_syncookies").value(1, "tcp_syncookies")
}

@index = "3.3"
CIS_rhel7_network state ipv6_configured() {
  let is_to_skip = condition_from_list_variable_match("skip_list", "3.3.1")
  @index = "3.3.1"
  @component = "Ensure IPv6 router advertisements are not accepted"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => {
    sysctl("net.ipv6.conf.all.accept_ra").value(0, "all_accept_ra")
    sysctl("net.ipv6.conf.default.accept_ra").value(0, "default_accept_ra")
  }

  let is_to_skip = condition_from_list_variable_match("skip_list", "3.3.2")
  @index = "3.3.2"
  @component = "Ensure IPv6 redirects are not accepted"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => {
    sysctl("net.ipv6.conf.all.accept_redirects").value(0, "all_accept_redirects")
    sysctl("net.ipv6.conf.default.accept_redirects").value(0, "default_accept_redirects")
  }

  # TOD verify -> modprobe -c | grep ipv6
  let is_to_skip = condition_from_list_variable_match("skip_list", "3.3.3")
  @index = "3.3.3"
  @component = "Ensure IPv6 is disabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => kernel_module("ipv6").configured("options ipv6 disable=1")
}

@index = "3.4"
CIS_rhel7_network state tcp_wrappers_configured() {
  let is_to_skip = condition_from_list_variable_match("skip_list", "3.4.1")
  @index = "3.4.1"
  @component = "Ensure TCP Wrappers is installed"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => {
    CIS_package("tcp_wrappers").installed()
    CIS_package("tcp_wrappers-libs").installed()
  }

  # DIY -> verify content of `cat /etc/hosts.allow`
  let is_to_skip = condition_from_list_variable_match("skip_list", "3.4.2")
  @index = "3.4.2"
  @component = "Ensure /etc/hosts.allow is configured"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => log_warn "3.4.2 (Ensure /etc/hosts.allow is configured) should be checked manually since a file content must be globally checked"

  let is_to_skip = condition_from_list_variable_match("skip_list", "3.4.3")
  @index = "3.4.3"
  @component = "Ensure /etc/hosts.deny is configured"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => file("/etc/hosts.deny").key_value_present("ALL", "ALL", ":")

  let is_to_skip = condition_from_list_variable_match("skip_list", "3.4.4")
  @index = "3.4.4"
  @component = "Ensure permissions on /etc/hosts.allow are configured"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => permissions("/etc/hosts.allow").value(644, root, root)

  let is_to_skip = condition_from_list_variable_match("skip_list", "3.4.5")
  @index = "3.4.5"
  @component = "Ensure permissions on /etc/hosts.deny are 644"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => permissions("/etc/hosts.deny").value(644, root, root)
}

@index = "3.5"
CIS_rhel7_network state uncommon_network_protocols_configured() {
  let is_to_skip = condition_from_list_variable_match("skip_list", "3.5.1")
  @index = "3.5.1"
  @component = "Ensure DCCP is disabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = false
  if ((SL1 | WL1) & !is_to_skip) => CIS_service("dccp").disabled()

  let is_to_skip = condition_from_list_variable_match("skip_list", "3.5.2")
  @index = "3.5.2"
  @component = "Ensure SCTP is disabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = false
  if ((SL1 | WL1) & !is_to_skip) => CIS_service("sctp").disabled()

  let is_to_skip = condition_from_list_variable_match("skip_list", "3.5.3")
  @index = "3.5.3"
  @component = "Ensure RDS is disabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = false
  if ((SL1 | WL1) & !is_to_skip) => CIS_service("rds").disabled()

  let is_to_skip = condition_from_list_variable_match("skip_list", "3.5.4")
  @index = "3.5.4"
  @component = "Ensure TIPC is disabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = false
  if ((SL1 | WL1) & !is_to_skip) => CIS_service("tipc").disabled()
}

@index = "3.6"
CIS_rhel7_network state firewall_configured() {
  let is_to_skip = condition_from_list_variable_match("skip_list", "3.6.1")
  @index = "3.6.1"
  @component = "Ensure iptables is installed"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_package("iptables").installed()

  # `iptables -L INPUT -v -n`
  let is_to_skip = condition_from_list_variable_match("skip_list", "3.6.2")
  @index = "3.6.2"
  @component = "Ensure iptables is installed"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => log_warn "3.6.2 (Ensure iptables is installed) should be checked manually"

  # `iptables -L -v -n`
  let is_to_skip = condition_from_list_variable_match("skip_list", "3.6.3")
  @index = "3.6.3"
  @component = "Ensure loopback traffic is configured"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => log_warn "3.6.3 (Ensure loopback traffic is configured) should be checked manually"

  # iptables -L -v -n
  let is_to_skip = condition_from_list_variable_match("skip_list", "3.6.4")
  @index = "3.6.4"
  @component = "Ensure outbound and established connections are configured"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => log_warn "3.6.4 (Ensure outbound and established connections are configured) should be checked manually"

  # `netstat -ln`
  let is_to_skip = condition_from_list_variable_match("skip_list", "3.6.5")
  @index = "3.6.5"
  @component = "Ensure firewall rules exist for all open ports"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => log_warn "3.6.5 (Ensure firewall rules exist for all open ports) should be checked manually"
}

@index = "4.1"
CIS_rhel7_logging_and_auditing state system_accounting_configured() {
  @index = "4.1.1"
  CIS_rhel7_logging_and_auditing().data_retention_configured()

  let is_to_skip = condition_from_list_variable_match("skip_list", "4.1.2")
  @index = "4.1.2"
  @component = "Ensure auditd service is enabled"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  if ((SL2 | WL2) & !is_to_skip) => CIS_service("auditd").disabled()

  # TODO: how to verify each linux line has the audit=1 parameter set?
  let is_to_skip = condition_from_list_variable_match("skip_list", "4.1.3")
  @index = "4.1.3"
  @component = "Ensure auditing for processes that start prior to auditd is enabled"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  if ((SL2 | WL2) & !is_to_skip) => log_warn "4.1.3 (Ensure auditing for processes that start prior to auditd is enabled) must be set manually"

  # depends on 32/64 system
  let is_to_skip = condition_from_list_variable_match("skip_list", "4.1.4")
  @index = "4.1.4"
  @component = "Ensure events that modify date and time information are collected"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  if ((SL2 | WL2) & !is_to_skip) => log_warn "4.1.4 (Ensure events that modify date and time information are collected) should be checked manually"

  # TODO: how to check multiline content
  let is_to_skip = condition_from_list_variable_match("skip_list", "4.1.5")
  @index = "4.1.5"
  @component = "Ensure events that modify user/group information are collected"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  if ((SL2 | WL2) & !is_to_skip) => log_warn "4.1.5 (Ensure events that modify user/group information are collected) must be set manually"
  
  # TODO: how to check multiline content
  let is_to_skip = condition_from_list_variable_match("skip_list", "4.1.6")
  @index = "4.1.6"
  @component = "Ensure events that modify the system"s network environment are collected"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  if ((SL2 | WL2) & !is_to_skip) => log_warn "4.1.6 (Ensure events that modify the system's network environment are collected) must be set manually"

  let is_to_skip = condition_from_list_variable_match("skip_list", "4.1.7")
  @index = "4.1.7"
  @component = "Ensure events that modify the system's Mandatory Access Controls are collected"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  if ((SL2 | WL2) & !is_to_skip) => file("/etc/audit/audit.rules").lines_present("-w /etc/selinux/ -p wa -k MAC-policy")

  # TODO: how to check multiline content
  let is_to_skip = condition_from_list_variable_match("skip_list", "4.1.8")
  @index = "4.1.8"
  @component = "Ensure login and logout events are collected"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  if ((SL2 | WL2) & !is_to_skip) => log_warn "4.1.8 (Ensure login and logout events are collected) must be set manually"

  # TODO: how to check multiline content
  let is_to_skip = condition_from_list_variable_match("skip_list", "4.1.9")
  @index = "4.1.9"
  @component = "Ensure session initiation information is collected"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  if ((SL2 | WL2) & !is_to_skip) => log_warn "4.1.9 (Ensure session initiation information is collected) must be set manually"

  # TODO: how to check multiline content
  let is_to_skip = condition_from_list_variable_match("skip_list", "4.1.10")
  @index = "4.1.10"
  @component = "Ensure discretionary access control permission modification events are collected"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  if ((SL2 | WL2) & !is_to_skip) => log_warn "4.1.10 (Ensure discretionary access control permission modification events are collected) must be set manually"

  # TODO: how to check multiline content
  let is_to_skip = condition_from_list_variable_match("skip_list", "4.1.11")
  @index = "4.1.11"
  @component = "Ensure unsuccessful unauthorized file access attempts are collected"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  if ((SL2 | WL2) & !is_to_skip) => log_warn "4.1.11 (Ensure unsuccessful unauthorized file access attempts are collected) must be set manually"

  # TODO: how to check multiline content
  let is_to_skip = condition_from_list_variable_match("skip_list", "4.1.12")
  @index = "4.1.12"
  @component = "Ensure use of privileged commands is collected"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  if ((SL2 | WL2) & !is_to_skip) => log_warn "4.1.12 (Ensure use of privileged commands is collected) must be set manually"

  # TODO: how to check multiline content
  let is_to_skip = condition_from_list_variable_match("skip_list", "4.1.13")
  @index = "4.1.13"
  @component = "Ensure successful file system mounts are collected"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  if ((SL2 | WL2) & !is_to_skip) => log_warn "4.1.13 (Ensure successful file system mounts are collected) must be set manually"

  # TODO: how to check multiline content
  let is_to_skip = condition_from_list_variable_match("skip_list", "4.1.14")
  @index = "4.1.14"
  @component = "Ensure file deletion events by users are collected"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  if ((SL2 | WL2) & !is_to_skip) => log_warn "4.1.14 (Ensure file deletion events by users are collected) must be set manually"

  # TODO: how to check multiline content
  let is_to_skip = condition_from_list_variable_match("skip_list", "4.1.15")
  @index = "4.1.15"
  @component = "Ensure changes to system administration scope (sudoers) is collected"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  if ((SL2 | WL2) & !is_to_skip) => log_warn "4.1.15 (Ensure changes to system administration scope (sudoers) is collected) must be set manually"

  let is_to_skip = condition_from_list_variable_match("skip_list", "4.1.16")
  @index = "4.1.16"
  @component = "Ensure system administrator actions (sudolog) are collected"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  if ((SL2 | WL2) & !is_to_skip) => file("/etc/audit/audit.rules").lines_present("-w /var/log/sudo.log -p wa -k actions")

  let is_to_skip = condition_from_list_variable_match("skip_list", "4.1.17")
  @index = "4.1.17"
  @component = "Ensure kernel module loading and unloading is collected"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  if ((SL2 | WL2) & !is_to_skip) => log_warn "4.1.17 (Ensure kernel module loading and unloading is collected) must be set manually"

  let is_to_skip = condition_from_list_variable_match("skip_list", "4.1.1")
  @index = "4.1.18"
  @component = "Ensure the audit configuration is immutable"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  if ((SL2 | WL2) & !is_to_skip) => CIS_variable("immutable_audit_configuration").match_from_command("""grep "^\s*[^#]" /etc/audit/audit.rules | tail -1""", "gpgcheck=1")

  # auditd must be reloaded after doing previous manipulations
  # ENFORCE ?
  # service("auditd").reload()
}

@index = "4.1.1"
CIS_rhel7_logging_and_auditing state data_retention_configured() {
  let is_to_skip = condition_from_list_variable_match("skip_list", "4.1.1.1")
  @index = "4.1.1.1"
  @component = "Ensure audit log storage size is configured"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = false
  if ((SL2 | WL2) & !is_to_skip) => log_info "4.1.1.1 (Ensure audit log storage size is configured) is not scored. Please configure the test manually if needed."

  let is_to_skip = condition_from_list_variable_match("skip_list", "4.1.1.2")
  @index = "4.1.1.2"
  @component = "Ensure system is disabled when audit logs are full"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  if ((SL2 | WL2) & !is_to_skip) => {
    file("/etc/audit/auditd.conf").key_value_present("space_left_action", "email", "=")
    file("/etc/audit/auditd.conf").key_value_present("action_mail_acct", "root", "=")
    file("/etc/audit/auditd.conf").key_value_present("admin_space_left_action", "halt", "=")
  }

  let is_to_skip = condition_from_list_variable_match("skip_list", "4.1.1.3")
  @index = "4.1.1.3"
  @component = "Ensure audit logs are not automatically deleted"
  @applicability = [ "SL2", "WL2" ]
  @is_scored = true
  if ((SL2 | WL2) & !is_to_skip) => file("/etc/audit/auditd.conf").key_value_present("max_log_file_action", "keep_logs", "=")
}

@index = "4.2"
CIS_rhel7_logging_and_auditing state logging_configured() {
  @index = "4.2.1"
  @component = "Configure rsyslog"
  CIS_rhel7_logging_and_auditing().rsyslog_configured()
  @index = "4.2.2"
  @component = "Configure syslog-ng"
  CIS_rhel7_logging_and_auditing().rsyslog_ng_configured()

  let is_to_skip = condition_from_list_variable_match("skip_list", "4.2.3")
  @index = "4.2.3"
  @component = "Ensure rsyslog or syslog-ng is installed"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_rhel7_logging_and_auditing().rsyslog_and_rsyslog_ng_installed()

  let is_to_skip = condition_from_list_variable_match("skip_list", "4.2.4")
  @index = "4.2.4"
  @component = "Ensure permissions on all logfiles are configured"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => log_warn "4.2.4 (Ensure permissions on all logfiles are configured) must be set manually"
}


@index = "4.2.1"
CIS_rhel7_logging_and_auditing state rsyslog_configured() {
  let is_to_skip = condition_from_list_variable_match("skip_list", "4.2.1.1")
  @index = "4.2.1.1"
  @component = "Ensure rsyslog Service is enabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_service("rsyslog").enabled()

  # manual check
  let is_to_skip = condition_from_list_variable_match("skip_list", "4.2.1.2")
  @index = "4.2.1.2"
  @component = "Ensure logging is configured"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = false
  if ((SL1 | WL1) & !is_to_skip) => log_info "4.2.1.2 (Ensure logging is configured) is not scored. Please configure the test manually if needed."

  let is_to_skip = condition_from_list_variable_match("skip_list", "4.2.1.3")
  @index = "4.2.1.3"
  @component = "Ensure rsyslog default file permissions configured"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => file("/etc/audit/auditd.conf").key_value_present("$FileCreateMode", "0640", " ")

  # use name of central log host, where to find it?
  let is_to_skip = condition_from_list_variable_match("skip_list", "4.2.1.4")
  @index = "4.2.1.4"
  @component = "Ensure rsyslog is configured to send logs to a remote log host"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => log_warn "4.2.1.4 (Ensure rsyslog is configured to send logs to a remote log host) must be set manually since Rudder has no knowledge of the central host log"

  let is_to_skip = condition_from_list_variable_match("skip_list", "4.2.1.5")
  @index = "4.2.1.5"
  @component = "Ensure remote rsyslog messages are only accepted on designated log hosts"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = false
  if ((SL1 | WL1) & !is_to_skip) => log_info "4.2.1.5 (Ensure remote rsyslog messages are only accepted on designated log hosts) is not scored. Please configure the test manually if needed."
}

@index = "4.2.2"
CIS_rhel7_logging_and_auditing state rsyslog_ng_configured() {
  let is_to_skip = condition_from_list_variable_match("skip_list", "4.2.2.1")
  @index = "4.2.2.1"
  @component = "Ensure syslog-ng service is enabled"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => CIS_service("rsyslog-ng").enabled()

  let is_to_skip = condition_from_list_variable_match("skip_list", "4.2.2.2")
  @index = "4.2.2.2"
  @component = "Ensure logging is configured"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = false
  if ((SL1 | WL1) & !is_to_skip) => log_info "4.2.2.2 (Ensure logging is configured) is not scored. Please configure the test manually if needed."

  # check perm is 640 or more restrictive
  let is_to_skip = condition_from_list_variable_match("skip_list", "4.2.2.3")
  @index = "4.2.2.3"
  @component = "Ensure syslog-ng default file permissions configured"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = true
  if ((SL1 | WL1) & !is_to_skip) => log_warn "4.2.2.3 (Ensure syslog-ng default file permissions configured) should be checked manually since several permissions could be valid"

  let is_to_skip = condition_from_list_variable_match("skip_list", "4.2.2.4")
  @index = "4.2.2.4"
  @component = "Ensure syslog-ng is configured to send logs to a remote log host"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = false
  if ((SL1 | WL1) & !is_to_skip) => log_info "4.2.2.4 (Ensure syslog-ng is configured to send logs to a remote log host) is not scored. Please configure the test manually if needed."

  let is_to_skip = condition_from_list_variable_match("skip_list", "4.2.2.5")
  @index = "4.2.2.5"
  @component = "Ensure remote syslog-ng messages are only accepted on designated log hosts"
  @applicability = [ "SL1", "WL1" ]
  @is_scored = false
  if ((SL1 | WL1) & !is_to_skip) => log_info "4.2.2.5 (Ensure remote syslog-ng messages are only accepted on designated log hosts) is not scored. Please configure the test manually if needed."
}

@index = "4.2.3"
CIS_rhel7_logging_and_auditing state rsyslog_and_rsyslog_ng_installed() {
  CIS_package("rsyslog").installed()
  CIS_package("rsyslog-ng").installed()
}
