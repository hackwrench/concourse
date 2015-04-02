<?php
require_once dirname(__FILE__) . "/../../../vendor/autoload.php";
require_once dirname(__FILE__) . "/thrift/ConcourseService.php";
require_once dirname(__FILE__) . "/thrift/shared/Types.php";
require_once dirname(__FILE__) . "/thrift/data/Types.php";
require_once dirname(__FILE__) . "/Convert.php";
require_once dirname(__FILE__) . "/Tag.php";
require_once dirname(__FILE__) . "/Link.php";

use Thrift\Transport\TSocket;
use Thrift\Transport\TBufferedTransport;
use Thrift\Protocol\TBinaryProtocolAccelerated;
use Thrift\ConcourseServiceClient;
use Thrift\Data\TObject;
use Thrift\Shared\TransactionToken;
use Thrift\Shared\Type;
use Thrift\Shared\AccessToken;

/**
 * Concourse is a self-tuning database that makes it easier for developers to 
 * quickly build robust and scalable systems. Concourse dynamically adapts on a 
 * per-application basis and offers features like automatic indexing, version 
 * control, and distributed ACID transactions within a big data platform that 
 * reduces operational complexity. Concourse abstracts away the management and 
 * tuning aspects of the database and allows developers to focus on what really 
 * matters.
 */
class Concourse {

    /**
     * Create a new client connection to the specified environment of the 
     * specified Concourse Server and return a handle to facilitate interaction.
     * @param type $host
     * @param type $port
     * @param type $username
     * @param type $password
     * @param type $environment
     * @return \Concourse
     */
    public static function connect($host = "localhost", $port = 1717, 
            $username = "admin", $password = "admin", $environment = "") {
        return new Concourse($host, $port, $username, $password, $environment);
    }

    private $host;
    private $port;
    private $username;
    private $password;
    private $environment;
    private $client;
    private $transaction;

    /**
     * Construct a new instance.
     * @param type $host
     * @param type $port
     * @param type $username
     * @param type $password
     * @param type $environment
     * @throws Exception
     */
    private function __construct($host, $port, $username, $password, $environment) {
        $this->host = $host;
        $this->port = $port;
        $this->username = $username;
        $this->password = $password;
        $this->environment = $environment;
        try {
            $socket = new TSocket($host, $port);
            $transport = new TBufferedTransport($socket);
            $protocol = new TBinaryProtocolAccelerated($transport);
            $this->client = new ConcourseServiceClient($protocol);
            $transport->open();
            $this->authenticate();
        }
        catch (TException $e) {
            throw new Exception("Could not connect to the Concourse Server at "
                    . $host . ":" . $port);
        }
    }

    /**
     * Login with the username and password and locally store the AccessToken 
     * to use with subsequent CRUD methods.
     * @throws Exception
     */
    private function authenticate() {
        try {
            $this->creds = $this->client->login($this->username, $this->password, 
                    $this->environment);
        }
        catch (TException $e) {
            throw e;
        }
    }

    /**
     * Append a value to a key within a record if it does not currently exist.
     * @param string $key
     * @param object $value
     * @param mixed $records
     * @return mixed
     * @throws InvalidArgumentException
     */
    public function add($key, $value, $records = null) {
        $array = func_get_arg(0);
        if(is_array($array)){
            $key = $array['key'];
            $value = $array['value'];
            $records = $array['records'];
            $records = empty($records) ? $array['record'] : $records;
        }
        $value = Convert::phpToThrift($value);
        if (empty($records)) {
            return $this->client->addKeyValue($key, $value, $this->creds, 
                    $this->transaction, $this->environment);
        }
        else if(is_array($records)){
            return $this->client->addKeyValueRecords($key, $value, $records,
                    $this->creds, $this->transaction, $this->environment);
        }
        else if(is_int($records) || is_long($records)){
            return $this->client->addKeyValueRecord($key, $value, $records,
                    $this->creds, $this->transaction, $this->environment);
        }
        else {
            throw new InvalidArgumentException("Must specify records as either"
                    . "an integer, long, array of integers or array of longs");
        }
    }
    
    /**
     * 
     * @param type $key
     * @param type $record
     * @param type $start
     * @param type $end
     * @return type
     */
    public function audit($key=null, $record=null, $start=null, $end=null){
        $array = func_get_arg(0);
        if(is_array($array)){
            $key = $array['key'];
            $record = $array['record'];
            $start = $array['start'];
            $end = $array['end'];
        }
        $start = is_string($start) ? Convert::stringToTime($start) : $start;
        $end = is_string($end) ? Convert::stringToTime($end) : $end;
        if(!empty($key) && !empty(record) && !empty($start) && !empty($end)){
            return $this->client->auditKeyRecordStartEnd($key, $record, $start,
                    $end, $this->creds, $this->transaction, $this->environment);
        }
        else if(!empty($key) && !empty($record) && !empty($start)){
            return $this->client->auditKeyRecordStart($key, $record, $start,
                    $this->creds, $this->transaction, $this->environment);
        }
        else if(!empty($key) && !empty($record)){
            return $this->client->auditKeyRecord($key, $record, $this->creds, 
                    $this->transaction, $this->environment);
        }
        else if(!empty($record) && !empty($start) && !empty($end)){
            return $this->client->auditRecordStartEnd($record, $start, $end,
                    $this->creds, $this->transaction, $this->environment);
        }
        else if(!empty($record) && !empty($start)){
            return $this->client->auditRecordStart($record, $start, $this->creds,
                    $this->transaction, $this->environment);
        }
        else{
            return $this->client->auditRecord($record, $this->creds, $this->transaction,
                    $this->environment);
        }
    }
    
    /**
     * 
     * @param type $keys
     * @param type $timestamp
     * @return type
     */
    public function browse($keys=null, $timestamp=null){
        $array = func_get_arg(0);
        if(is_array($array)){
            $keys = $array['keys'];
            $keys = empty($keys) ? $array['key'] : $keys;
            $timestamp = $array['timestamp'];
        }
        $timestamp = is_string($timestamp) ? Convert::stringToTime($timestamp):
            $timestamp;
        if(is_array($keys) && !empty($timestamp)){
            $data = $this->client->browseKeysTime($keys, $timestamp, $this->creds,
                    $this->transaction, $this->environment);
            
        }
        else if(is_array($keys)){
            $data = $this->client->browseKeys($keys, $this->creds,
                    $this->transaction, $this->environment);
        }
        else if(!empty($timestamp)){
            $data = $this->client->browseKeyTime($keys, $timestamp, $this->creds,
                    $this->transaction, $this->environment);
        }
        else{
            $data = $this->client->browseKey($keys, $this->creds, 
                    $this->transaction, $this->environment);
        }
        return Convert::phpify($data);
    }

}
