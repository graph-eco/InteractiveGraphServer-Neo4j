package org.graphviz.server.commands

import java.io.{File, FileInputStream, OutputStream}

import org.apache.commons.io.IOUtils
import org.graphviz.server._
import org.neo4j.driver.v1.Session
import org.springframework.beans.factory.annotation.Autowired

import scala.collection.JavaConversions._

class LazyCommandFromFile extends Command {
  var _source: File = null;

  def setSource(file: File): Unit = {
    _source = file;
  }

  override def execute(params: Params, ct: ContentTag, out: OutputStream) = {
    IOUtils.copy(new FileInputStream(_source), out);
  }
}

trait WithNeo4jServer {
  @Autowired
  var _neo4jConnector: Neo4jConnector = null;
}

class LoadGraphFromNeo4jServer extends JsonOutput with WithNeo4jServer {
  @Autowired
  var _graphMetaDB: GraphMetaDB = null;

  override def execute(params: Params): Map[String, Any] = {
    _neo4jConnector.execute { (session: Session) =>
      val nodes = queryNodes(session);
      val edges = queryEdges(session);
      Map[String, Any]("data" -> Map[String, Any]("nodes" -> nodes, "edges" -> edges));
    };
  }

  private def queryEdges(session: Session): Array[Map[String, Any]] = {
    session.run("MATCH p=()-->() RETURN p").map { result =>
      val rel = result.get("p").asPath().relationships().iterator().next();

      val from = rel.startNodeId();
      val to = rel.endNodeId();
      val id = rel.id();
      val label = rel.`type`();
      Map[String, Any]("id" -> id, "label" -> label, "from" -> from, "to" -> to);
    }.toArray
  }

  private def queryNodes(session: Session): Array[Map[String, Any]] = {
    session.run("MATCH (n) RETURN n").map { result =>
      val node = result.get("n").asNode();
      val id = node.id();
      val map = node.asMap();

      val labels = node.labels().toArray;
      var nodes = map.toMap + ("id" -> id) + ("labels" -> labels);
      val meta = _graphMetaDB.getNodeMeta(node);
      meta.getGroupName().foreach { x => nodes += ("group" -> x); }
      meta.getCaption().foreach { x => nodes += ("label" -> x); }
      meta.getSize().foreach { x => nodes += ("value" -> x); }
      meta.getPhotoURL().foreach { x => nodes += ("image" -> x); }

      nodes
    }.toArray
  }
}

