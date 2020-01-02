import chisel3.Module
import chisel3.{util, _}

class TestUnit(implicit val conf: CAHPConfig) extends Module{
  val io = IO(new Bundle{
    val ifOut = new IfUnitOut
    val exOut = Flipped(new ExUnitIn)
    val memOut = Flipped(new MemUnitIn)
    val wbOut = Flipped(new WbUnitIn)
    val stole = Output(Bool())

    val exUnitOut = new ExUnitOut
    val memWbOut = Flipped(new WbUnitIn)
  })

  val ifUnit = Module(new IfUnit())
  val idWbUnit = Module(new IdWbUnit())
  val exUnit = Module(new ExUnit())
  val memUnit = Module(new MemUnit())
  val rom = Module(new ExternalTestRom)
  val memA = Module(new ExternalRam())
  val memB = Module(new ExternalRam())

  ifUnit.io.in.romData := rom.io.romData

  ifUnit.io.in.jumpAddress := exUnit.io.out.jumpAddress
  ifUnit.io.in.jump := exUnit.io.out.jump
  ifUnit.io.idStole := idWbUnit.io.stole
  ifUnit.io.enable := true.B

  rom.io.romAddress := ifUnit.io.out.romAddress

  idWbUnit.io.idIn.instA := ifUnit.io.out.instAOut
  idWbUnit.io.idIn.instB := ifUnit.io.out.instBOut
  idWbUnit.io.idIn.pc := ifUnit.io.out.pcAddress

  idWbUnit.io.exMemIn := exUnit.io.memOut
  idWbUnit.io.exWbIn := exUnit.io.wbOut
  idWbUnit.io.memWbIn := memUnit.io.wbOut

  idWbUnit.io.idEnable := true.B
  idWbUnit.io.wbEnable := true.B
  idWbUnit.io.flush := false.B

  exUnit.io.in := idWbUnit.io.exOut
  exUnit.io.memIn := idWbUnit.io.memOut
  exUnit.io.wbIn := idWbUnit.io.wbOut
  exUnit.io.enable := true.B
  exUnit.io.flush := exUnit.io.out.jump

  memUnit.io.in := exUnit.io.memOut
  memUnit.io.wbIn := exUnit.io.wbOut
  memUnit.io.enable := true.B

  memA.io.address := memUnit.io.memA.address
  memA.io.in := memUnit.io.memA.in
  memA.io.writeEnable := memUnit.io.memA.writeEnable
  memUnit.io.memA.out := memA.io.out

  memB.io.address := memUnit.io.memB.address
  memB.io.in := memUnit.io.memB.in
  memB.io.writeEnable := memUnit.io.memB.writeEnable
  memUnit.io.memB.out := memB.io.out

  idWbUnit.io.wbIn := memUnit.io.wbOut

  io.ifOut := ifUnit.io.out
  io.exOut := idWbUnit.io.exOut
  io.memOut := idWbUnit.io.memOut
  io.wbOut := idWbUnit.io.wbOut
  io.stole := idWbUnit.io.stole

  io.exUnitOut := exUnit.io.out
  io.memWbOut := memUnit.io.wbOut
}
