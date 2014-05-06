Param(
  [alias("e")]
  $environment,
  [parameter(Mandatory=$true)]
  [alias("a")]
  $action,
  [alias("l")]
  $log_dir = ".\log",
  [alias("c")]
  $capture_dir,
  [alias("s")]
  $capture_src,
  $config_file
)

# Get java flags
function get_java_vars {
  $java_jmx_opts = @"
                   -Dcom.sun.management.jmxremote
                   -Dcom.sun.management.jmxremote.local.only=false
                   -Dcom.sun.management.jmxremote.ssl=false
                   -Dcom.sun.management.jmxremote.authenticate=false
                   -Dcom.sun.management.jmxremote.port=2222
"@
  return $java_jmx_opts
}

function start_wiff {
  # Start tcpdump with rotating log file
  # -s0 tells tcpdump to collect entire packet contents
  # -i selects the interface to collect from
  # -vvv gives us verbose logging/details
  # -C gives us the max size of per data file
  # -W is the number of rollover files - the file index will be appended to the end of each capture file
  # -w is the file name

  $jvm_opts = get_java_vars
if (([string]::IsNullOrEmpty($config_file)) -or !(Test-Path -Path $config_file)) {
    $config_file = "config/wiff-$environment.conf"
	}

  $windump_cmd = "WinDump -Z root -i $capture_src  -s0  -C 100 -W 30 -w $capture_dir\wiff_capture.pcap"
  $java_command = " $jvm_opts -jar wiff-0.1.0.jar --config-file=$config_file"
echo $windump_cmd
  # Make sure WIFF isn't running.
	if (Test-Path wiff.pid) {
	  $wiff_pid = Get-Content wiff.pid
	  $wiff = Get-Process -Id $wiff_pid -erroraction SilentlyContinue
    if ($wiff -ne $null) {
      echo "WIFF is already running"
      exit
    }
	}

  echo "Starting WIFF..."
  # Create directories for captures and log if they don't exist
  if (!([string]::IsNullOrEmpty($capture_dir)) -and !(Test-Path -Path $capture_dir)){
	  mkdir $capture_dir
  }
  if (!(Test-Path -Path $log_dir )){
	  mkdir $log_dir
  }

  # Stop WinDump
  $dump = Get-Process WinDump -erroraction SilentlyContinue
  if ($dump -ne $null) {
    Stop-Process -processname WinDump
  }

  if ($capture_src -ne $null) {
    Start-Process WinDump $windump_cmd
  }

  $wiff = Start-Process java $java_command -PassThru -NoNewWindow

  echo $wiff.Id > wiff.pid

  echo "WIFF has been started."
}

function stop_wiff {
 # Stop WinDump
 $dump = Get-Process WinDump -erroraction SilentlyContinue
 if ($dump -ne $null) {
   Stop-Process -processname WinDump -erroraction SilentlyContinue
 }

 # Make sure WIFF isn't running.
	if (Test-Path wiff.pid) {
	  $wiff_pid = Get-Content wiff.pid
    $wiff = Get-Process $wiff_pid -erroraction SilentlyContinue
     if ($wiff -ne $null) {
	    Stop-Process -Id $wiff_pid
    }
    rm wiff.pid
	} else {
    echo "WIFF is not running."
  }
}

$jvm_opts = get_java_vars
if ($action -eq "start") {
  start_wiff
} elseif ($action -eq "stop") {
  stop_wiff
} elseif ($action -eq "restart"){
  stop_wiff
  start_wiff
}
